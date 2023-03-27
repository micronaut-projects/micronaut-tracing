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
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
public enum KafkaConsumerAttributesGetter implements MessagingAttributesGetter<ConsumerRecord<?, ?>, Void> {

    INSTANCE;

    @Override
    public String getSystem(ConsumerRecord<?, ?> consumerRecord) {
        return "kafka";
    }

    @Override
    public String getDestinationKind(ConsumerRecord<?, ?> consumerRecord) {
        return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
    }

    @Override
    public String getDestination(ConsumerRecord<?, ?> consumerRecord) {
        return consumerRecord.topic();
    }

    @Override
    public boolean isTemporaryDestination(ConsumerRecord<?, ?> consumerRecord) {
        return false;
    }

    @Override
    @Nullable
    public String getConversationId(ConsumerRecord<?, ?> consumerRecord) {
        return null;
    }

    @Override
    public Long getMessagePayloadSize(ConsumerRecord<?, ?> consumerRecord) {
        return (long) consumerRecord.serializedValueSize();
    }

    @Override
    @Nullable
    public Long getMessagePayloadCompressedSize(ConsumerRecord<?, ?> consumerRecord) {
        return null;
    }

    @Override
    @Nullable
    public String getMessageId(ConsumerRecord<?, ?> consumerRecord, @Nullable Void unused) {
        return null;
    }

    @Override
    public List<String> getMessageHeader(ConsumerRecord<?, ?> consumerRecord, String name) {

        List<String> headers = new ArrayList<>();
        for (Header header : consumerRecord.headers().headers(name)) {
            headers.add(new String(header.value(), StandardCharsets.UTF_8));
        }
        return headers;
    }
}
