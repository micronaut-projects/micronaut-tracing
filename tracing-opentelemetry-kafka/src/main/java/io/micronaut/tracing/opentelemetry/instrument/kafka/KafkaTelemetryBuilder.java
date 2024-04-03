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
package io.micronaut.tracing.opentelemetry.instrument.kafka;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProducerRequest;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static io.micronaut.tracing.opentelemetry.instrument.kafka.KafkaAttributesExtractorUtils.putAttributes;

/**
 * Builder class for KafkaTelemetry object.
 *
 * @since 5.0.0
 */
@Internal
public final class KafkaTelemetryBuilder {

    static final String INSTRUMENTATION_NAME = "io.opentelemetry.micronaut-kafka-2.6";

    private final OpenTelemetry openTelemetry;
    private final KafkaTelemetryConfiguration kafkaTelemetryConfiguration;
    @SuppressWarnings("rawtypes")
    private final Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters;
    @SuppressWarnings("rawtypes")
    private final Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters;
    private final List<AttributesExtractor<KafkaProducerRequest, RecordMetadata>> producerAttributesExtractors = new ArrayList<>();
    private final List<AttributesExtractor<KafkaProcessRequest, Void>> consumerAttributesExtractors = new ArrayList<>();
    /**
     * Sets whether experimental attributes should be set to spans. These attributes may be changed or
     * removed in the future, so only enable this if you know you do not require attributes filled by
     * this instrumentation to be stable across versions.
     */
    private boolean captureExperimentalSpanAttributes;
    /**
     * Set whether to propagate trace context in producers. Enabled by default.
     */
    private boolean propagationEnabled = true;

    @SuppressWarnings("rawtypes")
    public KafkaTelemetryBuilder(OpenTelemetry openTelemetry, KafkaTelemetryConfiguration kafkaTelemetryConfiguration,
                                 Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters,
                                 Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters) {
        this.openTelemetry = openTelemetry;
        this.kafkaTelemetryConfiguration = kafkaTelemetryConfiguration;
        this.consumerTracingFilters = consumerTracingFilters;
        this.producerTracingFilters = producerTracingFilters;
    }

    public KafkaTelemetryBuilder addProducerAttributesExtractors(AttributesExtractor<KafkaProducerRequest, RecordMetadata> extractor) {
        producerAttributesExtractors.add(extractor);
        return this;
    }

    public KafkaTelemetryBuilder addConsumerAttributesExtractors(AttributesExtractor<KafkaProcessRequest, Void> extractor) {
        consumerAttributesExtractors.add(extractor);
        return this;
    }

    public KafkaTelemetry build() {

        KafkaInstrumenterFactory instrumenterFactory = new KafkaInstrumenterFactory(openTelemetry, INSTRUMENTATION_NAME);
        instrumenterFactory.setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes);

        Set<String> capturedHeaders = kafkaTelemetryConfiguration.getCapturedHeaders();
        if (CollectionUtils.isNotEmpty(capturedHeaders)) {
            consumerAttributesExtractors.add(new AttributesExtractor<>() {
                @Override
                public void onStart(AttributesBuilder attributes, Context parentContext, KafkaProcessRequest processRequest) {
                    putAttributes(kafkaTelemetryConfiguration, attributes, processRequest.getRecord().headers());
                }

                @Override
                public void onEnd(AttributesBuilder attributes, Context context, KafkaProcessRequest processRequest, Void unused, Throwable error) {
                    // do nothing in the end
                }
            });
            producerAttributesExtractors.add(new AttributesExtractor<>() {
                @Override
                public void onStart(AttributesBuilder attributes, Context parentContext, KafkaProducerRequest producerRequest) {
                    putAttributes(kafkaTelemetryConfiguration, attributes, producerRequest.getRecord().headers());
                }

                @Override
                public void onEnd(AttributesBuilder attributes, Context context, KafkaProducerRequest producerRequest, @Nullable RecordMetadata recordMetadata, @Nullable Throwable error) {
                    // do nothing in the end
                }
            });
        }

        return new KafkaTelemetry(
            openTelemetry,
            instrumenterFactory.createProducerInstrumenter(producerAttributesExtractors),
            instrumenterFactory.createConsumerProcessInstrumenter(consumerAttributesExtractors),
            producerTracingFilters, consumerTracingFilters, kafkaTelemetryConfiguration,
            propagationEnabled);
    }

    public void setCaptureExperimentalSpanAttributes(boolean captureExperimentalSpanAttributes) {
        this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    }

    public void setPropagationEnabled(boolean propagationEnabled) {
        this.propagationEnabled = propagationEnabled;
    }
}
