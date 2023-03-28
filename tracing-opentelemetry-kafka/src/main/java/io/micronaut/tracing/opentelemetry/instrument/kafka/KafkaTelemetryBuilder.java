package io.micronaut.tracing.opentelemetry.instrument.kafka;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProducerRequest;

import org.apache.kafka.clients.producer.RecordMetadata;

import static io.micronaut.tracing.opentelemetry.instrument.kafka.KafkaAttributesExtractorUtils.putAttributes;

/**
 * Builder class for KafkaTelemetry object.
 *
 * @since 4.6.0
 */
public final class KafkaTelemetryBuilder {

    static final String INSTRUMENTATION_NAME = "io.opentelemetry.micronaut-kafka-2.6";

    private final OpenTelemetry openTelemetry;
    private final KafkaTelemetryProperties kafkaTelemetryProperties;
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
    public KafkaTelemetryBuilder(OpenTelemetry openTelemetry, KafkaTelemetryProperties kafkaTelemetryProperties,
                                 Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters,
                                 Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters) {
        this.openTelemetry = openTelemetry;
        this.kafkaTelemetryProperties = kafkaTelemetryProperties;
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

        Set<String> capturedHeaders = kafkaTelemetryProperties.getCapturedHeaders();
        if (CollectionUtils.isNotEmpty(capturedHeaders)) {
            consumerAttributesExtractors.add(new AttributesExtractor<KafkaProcessRequest, Void>() {
                @Override
                public void onStart(AttributesBuilder attributes, Context parentContext, KafkaProcessRequest processRequest) {
                    putAttributes(kafkaTelemetryProperties, attributes, processRequest.getRecord().headers());
                }

                @Override
                public void onEnd(AttributesBuilder attributes, Context context, KafkaProcessRequest processRequest, Void unused, Throwable error) {
                    // do notting in the end
                }
            });
            producerAttributesExtractors.add(new AttributesExtractor<KafkaProducerRequest, RecordMetadata>() {
                @Override
                public void onStart(AttributesBuilder attributes, Context parentContext, KafkaProducerRequest producerRequest) {
                    putAttributes(kafkaTelemetryProperties, attributes, producerRequest.getRecord().headers());
                }

                @Override
                public void onEnd(AttributesBuilder attributes, Context context, KafkaProducerRequest producerRequest, @Nullable RecordMetadata recordMetadata, @Nullable Throwable error) {
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
