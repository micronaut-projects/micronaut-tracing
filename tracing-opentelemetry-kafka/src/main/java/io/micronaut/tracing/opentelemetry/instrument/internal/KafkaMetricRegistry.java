/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.opentelemetry.instrument.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;

import static io.micronaut.tracing.opentelemetry.instrument.internal.InstrumentDescriptor.INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER;
import static io.micronaut.tracing.opentelemetry.instrument.internal.InstrumentDescriptor.INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE;

/**
 * A registry mapping kafka metrics to corresponding OpenTelemetry metric definitions.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
final class KafkaMetricRegistry {

    private static final Set<String> GROUPS = new HashSet<>(Arrays.asList("consumer", "producer"));
    private static final Map<Class<?>, String> MEASURABLE_TO_INSTRUMENT_TYPE = new HashMap<>();
    private static final Map<String, String> DESCRIPTION_CACHE = new ConcurrentHashMap<>();

    static {
        Map<String, String> classNameToType = new HashMap<>();
        classNameToType.put(
            "org.apache.kafka.common.metrics.stats.Rate", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
        classNameToType.put(
            "org.apache.kafka.common.metrics.stats.Avg", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
        classNameToType.put(
            "org.apache.kafka.common.metrics.stats.Max", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
        classNameToType.put(
            "org.apache.kafka.common.metrics.stats.Value", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
        classNameToType.put(
            "org.apache.kafka.common.metrics.stats.CumulativeSum", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER);
        classNameToType.put(
            "org.apache.kafka.common.metrics.stats.CumulativeCount", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER);

        for (Map.Entry<String, String> entry : classNameToType.entrySet()) {
            try {
                MEASURABLE_TO_INSTRUMENT_TYPE.put(Class.forName(entry.getKey()), entry.getValue());
            } catch (ClassNotFoundException e) {
                // Class doesn't exist in this version of kafka client - skip
            }
        }
    }

    private KafkaMetricRegistry() {
    }

    @Nullable
    static RegisteredObservable getRegisteredObservable(Meter meter, KafkaMetric kafkaMetric) {
        // If metric is not a Measurable, we can't map it to an instrument
        Class<? extends Measurable> measurable = getMeasurable(kafkaMetric);
        if (measurable == null) {
            return null;
        }
        MetricName metricName = kafkaMetric.metricName();

        String matchingGroup = null;
        for (String group : GROUPS) {
            if (metricName.group().contains(group)) {
                matchingGroup = group;
                break;
            }
        }
        // Only map metrics that have a matching group
        if (matchingGroup == null) {
            return null;
        }
        String instrumentName = "kafka." + matchingGroup + "." + metricName.name().replace("-", "_");
        String instrumentDescription = DESCRIPTION_CACHE.computeIfAbsent(instrumentName, s -> metricName.description());
        String instrumentType = MEASURABLE_TO_INSTRUMENT_TYPE.getOrDefault(measurable, INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);

        InstrumentDescriptor instrumentDescriptor = toInstrumentDescriptor(instrumentType, instrumentName, instrumentDescription);
        Attributes attributes = toAttributes(metricName.tags());
        AutoCloseable observable = createObservable(meter, attributes, instrumentDescriptor, kafkaMetric);
        return new RegisteredObservable(metricName, instrumentDescriptor, attributes, observable);
    }

    @Nullable
    private static Class<? extends Measurable> getMeasurable(KafkaMetric kafkaMetric) {
        try {
            return kafkaMetric.measurable().getClass();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static InstrumentDescriptor toInstrumentDescriptor(
        String instrumentType, String instrumentName, String instrumentDescription) {
        switch (instrumentType) {
            case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE:
                return InstrumentDescriptor.createDoubleGauge(instrumentName, instrumentDescription);
            case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER:
                return InstrumentDescriptor.createDoubleCounter(instrumentName, instrumentDescription);
            default:
                // Continue below to throw
        }
        throw new IllegalStateException("Unrecognized instrument type. This is a bug.");
    }

    private static Attributes toAttributes(Map<String, String> tags) {
        AttributesBuilder attributesBuilder = Attributes.builder();
        tags.forEach(attributesBuilder::put);
        return attributesBuilder.build();
    }

    private static AutoCloseable createObservable(Meter meter,
                                                  Attributes attributes,
                                                  InstrumentDescriptor instrumentDescriptor,
                                                  KafkaMetric kafkaMetric) {
        Consumer<ObservableDoubleMeasurement> callback =
            observableMeasurement -> observableMeasurement.record(value(kafkaMetric), attributes);
        switch (instrumentDescriptor.getInstrumentType()) {
            case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE:
                return meter.gaugeBuilder(instrumentDescriptor.getName())
                    .setDescription(instrumentDescriptor.getDescription())
                    .buildWithCallback(callback);
            case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER:
                return meter.counterBuilder(instrumentDescriptor.getName())
                    .setDescription(instrumentDescriptor.getDescription())
                    .ofDoubles()
                    .buildWithCallback(callback);
            default:
                // Continue below to throw
        }
        //TODO: add support for other instrument types and value types as needed for new instruments. This should not happen.
        throw new IllegalStateException("Unrecognized instrument type. This is a bug.");
    }

    private static double value(KafkaMetric kafkaMetric) {
        return kafkaMetric.measurable().measure(kafkaMetric.config(), System.currentTimeMillis());
    }
}
