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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.tracing.opentelemetry.instrument.internal.KafkaInstrumenterFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * Builder class for kafkaTelemetry object.
 *
 * @since 4.6.0
 */
public final class KafkaTelemetryBuilder {

    static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-micronaut";

    private final OpenTelemetry openTelemetry;
    private final KafkaTelemetryProperties kafkaTelemetryProperties;
    @SuppressWarnings("rawtypes")
    private final Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters;
    @SuppressWarnings("rawtypes")
    private final Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters;
    private final List<AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata>> producerAttributesExtractors = new ArrayList<>();
    private final List<AttributesExtractor<ConsumerRecord<?, ?>, Void>> consumerAttributesExtractors = new ArrayList<>();
    /**
     * Sets whether experimental attributes should be set to spans. These attributes may be changed or
     * removed in the future, so only enable this if you know you do not require attributes filled by
     * this instrumentation to be stable across versions.
     */
    private boolean captureExperimentalSpanAttributes;
    /**
     * Set whether to propagate trace context in producers. Enabled by default.
     *
     * <p>You will need to disable this if there are kafka consumers using kafka-clients version prior
     * to 0.11, since those old versions do not support headers, and attaching trace context
     * propagation headers upstream causes those consumers to fail when reading the messages.
     */
    private boolean propagationEnabled = true;

    @SuppressWarnings("rawtypes")
    public KafkaTelemetryBuilder(OpenTelemetry openTelemetry, KafkaTelemetryProperties kafkaTelemetryProperties,
                                 Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters,
                                 Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters) {
        this.openTelemetry = openTelemetry;
        this.kafkaTelemetryProperties = kafkaTelemetryProperties;
        this.consumerTracingFilters = consumerTracingFilters;
        this.producerTracingFilters = producerTracingFilters;
    }

    public KafkaTelemetryBuilder addProducerAttributesExtractors(AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata> extractor) {
        producerAttributesExtractors.add(extractor);
        return this;
    }

    public KafkaTelemetryBuilder addConsumerAttributesExtractors(AttributesExtractor<ConsumerRecord<?, ?>, Void> extractor) {
        consumerAttributesExtractors.add(extractor);
        return this;
    }

    public KafkaTelemetry build() {

        KafkaInstrumenterFactory instrumenterFactory = new KafkaInstrumenterFactory(openTelemetry, INSTRUMENTATION_NAME);
        instrumenterFactory.setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes);

        Set<String> capturedHeaders = kafkaTelemetryProperties.getCapturedHeaders();
        if (CollectionUtils.isNotEmpty(capturedHeaders)) {
            consumerAttributesExtractors.add(new AttributesExtractor<ConsumerRecord<?, ?>, Void>() {
                @Override
                public void onStart(AttributesBuilder attributes, Context parentContext, ConsumerRecord<?, ?> consumerRecord) {
                    KafkaAttributesExtractorUtils.putAttributes(kafkaTelemetryProperties, attributes, consumerRecord.headers());
                }

                @Override
                public void onEnd(AttributesBuilder attributes, Context context, ConsumerRecord<?, ?> consumerRecord, Void unused, Throwable error) {
                    // do notting in the end
                }
            });
            producerAttributesExtractors.add(new AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata>() {
                @Override
                public void onStart(AttributesBuilder attributes, Context parentContext, ProducerRecord<?, ?> producerRecord) {
                    KafkaAttributesExtractorUtils.putAttributes(kafkaTelemetryProperties, attributes, producerRecord.headers());
                }

                @Override
                public void onEnd(AttributesBuilder attributes, Context context, ProducerRecord<?, ?> producerRecord, @Nullable RecordMetadata recordMetadata, @Nullable Throwable error) {
                    // do notting in the end
                }
            });
        }

        return new KafkaTelemetry(
            openTelemetry,
            instrumenterFactory.createProducerInstrumenter(producerAttributesExtractors),
            instrumenterFactory.createConsumerOperationInstrumenter(MessageOperation.RECEIVE, consumerAttributesExtractors),
            producerTracingFilters, consumerTracingFilters, kafkaTelemetryProperties,
            propagationEnabled);
    }

    public void setCaptureExperimentalSpanAttributes(boolean captureExperimentalSpanAttributes) {
        this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    }

    public void setPropagationEnabled(boolean propagationEnabled) {
        this.propagationEnabled = propagationEnabled;
    }
}
