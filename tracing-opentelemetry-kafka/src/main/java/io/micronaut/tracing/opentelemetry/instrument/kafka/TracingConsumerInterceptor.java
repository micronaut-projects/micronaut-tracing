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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * Default tracing consumer kafka listener.
 *
 * @param <K> key class
 * @param <V> value class
 *
 * @since 4.6.0
 */
public class TracingConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

    private String consumerGroup;
    private String clientId;

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {

        KafkaTelemetry kafkaTelemetry = KafkaTelemetryHelper.getKafkaTelemetry();
        List<ConsumerRecord<K, V>> recordsToTrace = new ArrayList<>();

        for (ConsumerRecord<K, V> record : records) {
            if (kafkaTelemetry.excludeTopic(record.topic())) {
                continue;
            }
            if (!filterRecord(record, consumerGroup, clientId)) {
                continue;
            }
            recordsToTrace.add(record);
        }
        kafkaTelemetry.buildAndFinishSpan(recordsToTrace, consumerGroup, clientId);

        return records;
    }

    /**
     * Override this method if you need to set custom condition or logic to filter message to trace.
     *
     * @param record consumer record
     * @param consumerGroup consumer group
     * @param clientId clinet ID
     *
     * @return true if this record need to trace, false - otherwise
     */
    public boolean filterRecord(ConsumerRecord<K, V> record, String consumerGroup, String clientId) {
        return true;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
        consumerGroup = Objects.toString(configs.get(ConsumerConfig.GROUP_ID_CONFIG), null);
        clientId = Objects.toString(configs.get(ConsumerConfig.CLIENT_ID_CONFIG), null);
    }
}
