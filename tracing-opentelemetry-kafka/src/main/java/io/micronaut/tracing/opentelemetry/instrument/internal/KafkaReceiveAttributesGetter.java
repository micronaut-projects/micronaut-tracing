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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
public enum KafkaReceiveAttributesGetter implements MessagingAttributesGetter<ConsumerRecords<?, ?>, Void> {

    INSTANCE;

    @Override
    public String system(ConsumerRecords<?, ?> consumerRecords) {
        return "kafka";
    }

    @Override
    public String destinationKind(ConsumerRecords<?, ?> consumerRecords) {
        return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
    }

    @Override
    @Nullable
    public String destination(ConsumerRecords<?, ?> consumerRecords) {
        Set<String> topics = new HashSet<>();
        for (TopicPartition partition : consumerRecords.partitions()) {
            topics.add(partition.topic());
        }
        return topics.size() == 1 ? topics.iterator().next() : null;
    }

    @Override
    public boolean temporaryDestination(ConsumerRecords<?, ?> consumerRecords) {
        return false;
    }

    @Override
    @Nullable
    public String protocol(ConsumerRecords<?, ?> consumerRecords) {
        return null;
    }

    @Override
    @Nullable
    public String protocolVersion(ConsumerRecords<?, ?> consumerRecords) {
        return null;
    }

    @Override
    @Nullable
    public String url(ConsumerRecords<?, ?> consumerRecords) {
        return null;
    }

    @Override
    @Nullable
    public String conversationId(ConsumerRecords<?, ?> consumerRecords) {
        return null;
    }

    @Override
    @Nullable
    public Long messagePayloadSize(ConsumerRecords<?, ?> consumerRecords) {
        return null;
    }

    @Override
    @Nullable
    public Long messagePayloadCompressedSize(ConsumerRecords<?, ?> consumerRecords) {
        return null;
    }

    @Override
    @Nullable
    public String messageId(ConsumerRecords<?, ?> consumerRecords, @Nullable Void unused) {
        return null;
    }

    @Override
    public List<String> header(ConsumerRecords<?, ?> records, String name) {
        List<String> headers = new ArrayList<>();
        for (ConsumerRecord<?, ?> record : records) {
            for (Header header : record.headers().headers(name)) {
                headers.add(new String(header.value(), StandardCharsets.UTF_8));
            }
        }
        return headers;
    }
}
