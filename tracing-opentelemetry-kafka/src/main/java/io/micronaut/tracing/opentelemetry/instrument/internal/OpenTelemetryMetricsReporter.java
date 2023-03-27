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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;

import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MetricsReporter} which bridges Kafka metrics to OpenTelemetry metrics.
 *
 * <p>To configure, use:
 *
 * <pre>{@code
 * // KafkaTelemetry.create(OpenTelemetry).metricConfigProperties()
 * }</pre>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
public final class OpenTelemetryMetricsReporter implements MetricsReporter {

    public static final String CONFIG_KEY_OPENTELEMETRY_INSTANCE = "opentelemetry.instance";
    public static final String CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME = "opentelemetry.instrumentation_name";

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryMetricsReporter.class);

    private static final Object LOCK = new Object();
    @GuardedBy("lock")
    private static final List<RegisteredObservable> REGISTERED_OBSERVABLES = new ArrayList<>();

    private volatile Meter meter;

    /**
     * Reset for test by resetting the {@link #meter} to {@code null} and closing all registered
     * instruments.
     */
    static void resetForTest() {
        closeAllInstruments();
    }

    // Visible for test
    static List<RegisteredObservable> getRegisteredObservables() {
        synchronized (LOCK) {
            return new ArrayList<>(REGISTERED_OBSERVABLES);
        }
    }

    @Override
    public void init(List<KafkaMetric> metrics) {
        metrics.forEach(this::metricChange);
    }

    @Override
    public void metricChange(KafkaMetric metric) {
        Meter currentMeter = meter;
        if (currentMeter == null) {
            // Ignore if meter hasn't been initialized in configure(Map<String, ?)
            return;
        }

        RegisteredObservable registeredObservable =
            KafkaMetricRegistry.getRegisteredObservable(currentMeter, metric);
        if (registeredObservable == null) {
            LOG.info("Metric changed but cannot map to instrument: {}", metric.metricName());
            return;
        }

        Set<AttributeKey<?>> attributeKeys = registeredObservable.getAttributes().asMap().keySet();
        synchronized (LOCK) {
            for (Iterator<RegisteredObservable> it = REGISTERED_OBSERVABLES.iterator(); it.hasNext(); ) {
                RegisteredObservable curRegisteredObservable = it.next();
                Set<AttributeKey<?>> curAttributeKeys = curRegisteredObservable.getAttributes().asMap().keySet();
                if (curRegisteredObservable.getKafkaMetricName().equals(metric.metricName())) {
                    LOG.info("Replacing instrument: {}", curRegisteredObservable);
                    closeInstrument(curRegisteredObservable.getObservable());
                    it.remove();
                } else if (curRegisteredObservable.getInstrumentDescriptor().equals(registeredObservable.getInstrumentDescriptor())
                    && attributeKeys.size() > curAttributeKeys.size()
                    && attributeKeys.containsAll(curAttributeKeys)) {
                    LOG.info("Replacing instrument with higher dimension version: {}", curRegisteredObservable);
                    closeInstrument(curRegisteredObservable.getObservable());
                    it.remove();
                }
            }

            REGISTERED_OBSERVABLES.add(registeredObservable);
        }
    }

    @Override
    public void metricRemoval(KafkaMetric metric) {
        LOG.info("Metric removed: {}", metric.metricName());
        synchronized (LOCK) {
            for (Iterator<RegisteredObservable> it = REGISTERED_OBSERVABLES.iterator(); it.hasNext(); ) {
                RegisteredObservable current = it.next();
                if (current.getKafkaMetricName().equals(metric.metricName())) {
                    closeInstrument(current.getObservable());
                    it.remove();
                }
            }
        }
    }

    @Override
    public void close() {
        closeAllInstruments();
    }

    private static void closeAllInstruments() {
        synchronized (LOCK) {
            for (Iterator<RegisteredObservable> it = REGISTERED_OBSERVABLES.iterator(); it.hasNext(); ) {
                closeInstrument(it.next().getObservable());
                it.remove();
            }
        }
    }

    private static void closeInstrument(AutoCloseable observable) {
        try {
            observable.close();
        } catch (Exception e) {
            throw new IllegalStateException("Error occurred closing instrument", e);
        }
    }

    @Override
    public void configure(Map<String, ?> configs) {
        OpenTelemetry openTelemetry = getProperty(configs, CONFIG_KEY_OPENTELEMETRY_INSTANCE, OpenTelemetry.class);
        String instrumentationName = getProperty(configs, CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME, String.class);
        String instrumentationVersion = EmbeddedInstrumentationProperties.findVersion(instrumentationName);

        MeterBuilder meterBuilder = openTelemetry.meterBuilder(instrumentationName);
        if (instrumentationVersion != null) {
            meterBuilder.setInstrumentationVersion(instrumentationVersion);
        }
        meter = meterBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getProperty(Map<String, ?> configs, String key, Class<T> requiredType) {
        Object value = configs.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required configuration property: " + key);
        }
        if (!requiredType.isInstance(value)) {
            throw new IllegalStateException("Configuration property " + key + " is not instance of " + requiredType.getSimpleName());
        }
        return (T) value;
    }
}
