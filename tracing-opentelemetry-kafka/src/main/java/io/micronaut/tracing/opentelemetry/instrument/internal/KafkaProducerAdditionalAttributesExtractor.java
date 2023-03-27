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

import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import static io.opentelemetry.api.common.AttributeKey.longKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
final class KafkaProducerAdditionalAttributesExtractor implements AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata> {

    //TODO:remove this constant when this attribute appears in SemanticAttributes
    private static final AttributeKey<Long> MESSAGING_KAFKA_MESSAGE_OFFSET = longKey("messaging.kafka.message.offset");

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, ProducerRecord<?, ?> producerRecord) {
        if (producerRecord.value() == null) {
            attributes.put(SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE, true);
        }
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        ProducerRecord<?, ?> producerRecord,
        @Nullable RecordMetadata recordMetadata,
        @Nullable Throwable error) {

        if (recordMetadata != null) {
            attributes.put(SemanticAttributes.MESSAGING_KAFKA_PARTITION, recordMetadata.partition());
            attributes.put(MESSAGING_KAFKA_MESSAGE_OFFSET, recordMetadata.offset());
        }
    }
}
