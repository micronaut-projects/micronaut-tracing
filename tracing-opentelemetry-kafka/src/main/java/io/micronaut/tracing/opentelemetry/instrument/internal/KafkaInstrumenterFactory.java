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

import java.util.Collections;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
public final class KafkaInstrumenterFactory {

    private final OpenTelemetry openTelemetry;
    private final String instrumentationName;

    private ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.getDefault();
    private boolean captureExperimentalSpanAttributes;
    private boolean messagingReceiveInstrumentationEnabled = true;

    public KafkaInstrumenterFactory(OpenTelemetry openTelemetry, String instrumentationName) {
        this.openTelemetry = openTelemetry;
        this.instrumentationName = instrumentationName;
    }

    public Instrumenter<ProducerRecord<?, ?>, RecordMetadata> createProducerInstrumenter() {
        return createProducerInstrumenter(Collections.emptyList());
    }

    public Instrumenter<ProducerRecord<?, ?>, RecordMetadata> createProducerInstrumenter(Iterable<AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata>> extractors) {

        KafkaProducerAttributesGetter getter = KafkaProducerAttributesGetter.INSTANCE;
        MessageOperation operation = MessageOperation.SEND;

        return Instrumenter.<ProducerRecord<?, ?>, RecordMetadata>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractors(extractors)
            .addAttributesExtractor(new KafkaProducerAdditionalAttributesExtractor())
            .setErrorCauseExtractor(errorCauseExtractor)
            .buildInstrumenter(SpanKindExtractor.alwaysProducer());
    }

    public Instrumenter<ConsumerRecords<?, ?>, Void> createConsumerReceiveInstrumenter() {
        KafkaReceiveAttributesGetter getter = KafkaReceiveAttributesGetter.INSTANCE;
        MessageOperation operation = MessageOperation.RECEIVE;

        return Instrumenter.<ConsumerRecords<?, ?>, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operation))
            .setErrorCauseExtractor(errorCauseExtractor)
            .setEnabled(messagingReceiveInstrumentationEnabled)
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    }

    public Instrumenter<ConsumerRecord<?, ?>, Void> createConsumerProcessInstrumenter() {
        return createConsumerOperationInstrumenter(MessageOperation.PROCESS, Collections.emptyList());
    }

    public Instrumenter<ConsumerRecord<?, ?>, Void> createConsumerOperationInstrumenter(
        MessageOperation operation,
        Iterable<AttributesExtractor<ConsumerRecord<?, ?>, Void>> extractors) {

        KafkaConsumerAttributesGetter getter = KafkaConsumerAttributesGetter.INSTANCE;

        InstrumenterBuilder<ConsumerRecord<?, ?>, Void> builder =
            Instrumenter.<ConsumerRecord<?, ?>, Void>builder(
                    openTelemetry,
                    instrumentationName,
                    MessagingSpanNameExtractor.create(getter, operation))
                .addAttributesExtractor(new KafkaConsumerAdditionalAttributesExtractor())
                .addAttributesExtractors(extractors)
                .setErrorCauseExtractor(errorCauseExtractor);
        if (captureExperimentalSpanAttributes) {
            builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
        }

        if (messagingReceiveInstrumentationEnabled) {
            builder.addSpanLinksExtractor(
                new PropagatorBasedSpanLinksExtractor<>(openTelemetry.getPropagators().getTextMapPropagator(), KafkaConsumerRecordGetter.INSTANCE));
            return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
        } else {
            return builder.buildConsumerInstrumenter(KafkaConsumerRecordGetter.INSTANCE);
        }
    }

    public Instrumenter<ConsumerRecords<?, ?>, Void> createBatchProcessInstrumenter() {
        KafkaBatchProcessAttributesGetter getter = KafkaBatchProcessAttributesGetter.INSTANCE;
        MessageOperation operation = MessageOperation.PROCESS;

        return Instrumenter.<ConsumerRecords<?, ?>, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operation))
            .addSpanLinksExtractor(new KafkaBatchProcessSpanLinksExtractor(openTelemetry.getPropagators().getTextMapPropagator()))
            .setErrorCauseExtractor(errorCauseExtractor)
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    }

    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    public String getInstrumentationName() {
        return instrumentationName;
    }

    public ErrorCauseExtractor getErrorCauseExtractor() {
        return errorCauseExtractor;
    }

    public void setErrorCauseExtractor(ErrorCauseExtractor errorCauseExtractor) {
        this.errorCauseExtractor = errorCauseExtractor;
    }

    public boolean isCaptureExperimentalSpanAttributes() {
        return captureExperimentalSpanAttributes;
    }

    public void setCaptureExperimentalSpanAttributes(boolean captureExperimentalSpanAttributes) {
        this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    }

    public boolean isMessagingReceiveInstrumentationEnabled() {
        return messagingReceiveInstrumentationEnabled;
    }

    public void setMessagingReceiveInstrumentationEnabled(boolean messagingReceiveInstrumentationEnabled) {
        this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    }
}
