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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.Uuid;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Consumer wrapper for Open Telemetry instrumentation purposes.
 *
 * @param <K> key class
 * @param <V> value class
 *
 * */
@Internal
final class MicronautOtelKafkaConsumer<K, V> implements Consumer<K, V> {

    private final Consumer<K, V> consumer;
    private final KafkaTelemetry kafkaTelemetry;

    public MicronautOtelKafkaConsumer(Consumer<K, V> consumer, KafkaTelemetry kafkaTelemetry) {
        this.consumer = consumer;
        this.kafkaTelemetry = kafkaTelemetry;
    }

    @Override
    public Set<TopicPartition> assignment() {
        return consumer.assignment();
    }

    @Override
    public Set<String> subscription() {
        return consumer.subscription();
    }

    @Override
    public void subscribe(Collection<String> collection) {
        consumer.subscribe(collection);
    }

    @Override
    public void subscribe(Collection<String> collection, ConsumerRebalanceListener consumerRebalanceListener) {
        consumer.subscribe(collection, consumerRebalanceListener);

    }

    @Override
    public void assign(Collection<TopicPartition> collection) {
        consumer.assign(collection);
    }

    @Override
    public void subscribe(Pattern pattern, ConsumerRebalanceListener consumerRebalanceListener) {
        consumer.subscribe(pattern, consumerRebalanceListener);
    }

    @Override
    public void subscribe(Pattern pattern) {
        consumer.subscribe(pattern);
    }

    @Override
    public void unsubscribe() {
        consumer.unsubscribe();
    }

    @Override
    public ConsumerRecords<K, V> poll(long l) {
        ConsumerRecords<K, V> records = consumer.poll(l);
        traceConsumerRecords(records);
        return records;
    }

    @Override
    public ConsumerRecords<K, V> poll(Duration duration) {
        ConsumerRecords<K, V> records = consumer.poll(duration);
        traceConsumerRecords(records);
        return records;
    }

    private void traceConsumerRecords(ConsumerRecords<K, V> consumerRecords) {
        List<ConsumerRecord<K, V>> recordsToTrace = new ArrayList<>();
        for (ConsumerRecord<K, V> record : consumerRecords) {
            if (kafkaTelemetry.excludeTopic(record.topic()) || !kafkaTelemetry.filterConsumerRecord(record, consumer)) {
                continue;
            }
            recordsToTrace.add(record);
        }
        kafkaTelemetry.buildAndFinishSpan(recordsToTrace, consumer);
    }

    @Override
    public void commitSync() {
        consumer.commitSync();
    }

    @Override
    public void commitSync(Duration duration) {
        consumer.commitSync(duration);
    }

    @Override
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> map) {
        consumer.commitSync(map);
    }

    @Override
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> map, Duration duration) {
        consumer.commitSync(map, duration);
    }

    @Override
    public void commitAsync() {
        consumer.commitAsync();
    }

    @Override
    public void commitAsync(OffsetCommitCallback offsetCommitCallback) {
        consumer.commitAsync(offsetCommitCallback);
    }

    @Override
    public void commitAsync(Map<TopicPartition, OffsetAndMetadata> map, OffsetCommitCallback offsetCommitCallback) {
        consumer.commitAsync(map, offsetCommitCallback);
    }

    @Override
    public void seek(TopicPartition topicPartition, long l) {
        consumer.seek(topicPartition, l);
    }

    @Override
    public void seek(TopicPartition topicPartition, OffsetAndMetadata offsetAndMetadata) {
        consumer.seek(topicPartition, offsetAndMetadata);
    }

    @Override
    public void seekToBeginning(Collection<TopicPartition> collection) {
        consumer.seekToBeginning(collection);
    }

    @Override
    public void seekToEnd(Collection<TopicPartition> collection) {
        consumer.seekToEnd(collection);
    }

    @Override
    public long position(TopicPartition topicPartition) {
       return consumer.position(topicPartition);
    }

    @Override
    public long position(TopicPartition topicPartition, Duration duration) {
        return consumer.position(topicPartition, duration);
    }

    @Override
    public OffsetAndMetadata committed(TopicPartition topicPartition) {
        return consumer.committed(topicPartition);
    }

    @Override
    public OffsetAndMetadata committed(TopicPartition topicPartition, Duration duration) {
        return consumer.committed(topicPartition, duration);
    }

    @Override
    public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> set) {
        return consumer.committed(set);
    }

    @Override
    public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> set, Duration duration) {
        return consumer.committed(set, duration);
    }

    @Override
    public Uuid clientInstanceId(Duration duration) {
        return consumer.clientInstanceId(duration);
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return consumer.metrics();
    }

    @Override
    public List<PartitionInfo> partitionsFor(String s) {
        return consumer.partitionsFor(s);
    }

    @Override
    public List<PartitionInfo> partitionsFor(String s, Duration duration) {
        return consumer.partitionsFor(s, duration);
    }

    @Override
    public Map<String, List<PartitionInfo>> listTopics() {
        return consumer.listTopics();
    }

    @Override
    public Map<String, List<PartitionInfo>> listTopics(Duration duration) {
        return consumer.listTopics(duration);
    }

    @Override
    public Set<TopicPartition> paused() {
        return consumer.paused();
    }

    @Override
    public void pause(Collection<TopicPartition> collection) {
        consumer.pause(collection);
    }

    @Override
    public void resume(Collection<TopicPartition> collection) {
        consumer.resume(collection);
    }

    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> map) {
        return consumer.offsetsForTimes(map);
    }

    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> map, Duration duration) {
        return consumer.offsetsForTimes(map, duration);
    }

    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> collection) {
        return consumer.beginningOffsets(collection);
    }

    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> collection, Duration duration) {
        return consumer.beginningOffsets(collection, duration);
    }

    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> collection) {
        return consumer.endOffsets(collection);
    }

    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> collection, Duration duration) {
        return consumer.endOffsets(collection, duration);
    }

    @Override
    public OptionalLong currentLag(TopicPartition topicPartition) {
        return consumer.currentLag(topicPartition);
    }

    @Override
    public ConsumerGroupMetadata groupMetadata() {
        return consumer.groupMetadata();
    }

    @Override
    public void enforceRebalance() {
        consumer.enforceRebalance();
    }

    @Override
    public void enforceRebalance(String s) {
        consumer.enforceRebalance(s);
    }

    @Override
    public void close() {
        consumer.close();
    }

    @Override
    public void close(Duration duration) {
        consumer.close(duration);
    }

    @Override
    public void wakeup() {
        consumer.wakeup();
    }
}
