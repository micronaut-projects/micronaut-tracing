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
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
enum KafkaBatchProcessAttributesGetter implements MessagingAttributesGetter<ConsumerRecords<?, ?>, Void> {

    INSTANCE;

    @Override
    public String getSystem(ConsumerRecords<?, ?> records) {
        return "kafka";
    }

    @Override
    public String getDestinationKind(ConsumerRecords<?, ?> records) {
        return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
    }

    @Nullable
    @Override
    public String getDestination(ConsumerRecords<?, ?> records) {
        Set<String> topics = new HashSet<>();
        for (TopicPartition partition : records.partitions()) {
            topics.add(partition.topic());
        }
        // only return topic when there's exactly one in the batch
        return topics.size() == 1 ? topics.iterator().next() : null;
    }

    @Override
    public boolean isTemporaryDestination(ConsumerRecords<?, ?> records) {
        return false;
    }

    @Nullable
    @Override
    public String getConversationId(ConsumerRecords<?, ?> records) {
        return null;
    }

    @Nullable
    @Override
    public Long getMessagePayloadSize(ConsumerRecords<?, ?> records) {
        return null;
    }

    @Nullable
    @Override
    public Long getMessagePayloadCompressedSize(ConsumerRecords<?, ?> records) {
        return null;
    }

    @Nullable
    @Override
    public String getMessageId(ConsumerRecords<?, ?> records, @Nullable Void unused) {
        return null;
    }

    @Override
    public List<String> getMessageHeader(ConsumerRecords<?, ?> records, String name) {
        List<String> headers = new ArrayList<>();
        for (ConsumerRecord<?, ?> record : records) {
            for (Header header : record.headers().headers(name)) {
                headers.add(new String(header.value(), StandardCharsets.UTF_8));
            }
        }
        return headers;
    }
}
