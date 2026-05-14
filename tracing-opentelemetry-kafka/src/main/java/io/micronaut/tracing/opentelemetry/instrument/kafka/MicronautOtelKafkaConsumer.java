/*
 * Copyright 2017-2026 original authors
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
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.CloseOptions;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.consumer.SubscriptionPattern;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.metrics.KafkaMetric;

import java.time.Duration;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
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
    private ActiveRecordContext activeRecordContext;

    public MicronautOtelKafkaConsumer(Consumer<K, V> consumer, KafkaTelemetry kafkaTelemetry) {
        this.consumer = consumer;
        this.kafkaTelemetry = kafkaTelemetry;
    }

    @Override
    public Set<TopicPartition> assignment() {
        return withInactiveContext(consumer::assignment);
    }

    @Override
    public Set<String> subscription() {
        return withInactiveContext(consumer::subscription);
    }

    @Override
    public void subscribe(Collection<String> collection) {
        runWithInactiveContext(() -> consumer.subscribe(collection));
    }

    @Override
    public void subscribe(Collection<String> collection, ConsumerRebalanceListener consumerRebalanceListener) {
        runWithInactiveContext(() -> consumer.subscribe(collection, consumerRebalanceListener));
    }

    @Override
    public void assign(Collection<TopicPartition> collection) {
        runWithInactiveContext(() -> consumer.assign(collection));
    }

    @Override
    public void subscribe(Pattern pattern, ConsumerRebalanceListener consumerRebalanceListener) {
        runWithInactiveContext(() -> consumer.subscribe(pattern, consumerRebalanceListener));
    }

    @Override
    public void subscribe(Pattern pattern) {
        runWithInactiveContext(() -> consumer.subscribe(pattern));
    }

    @Override
    public void subscribe(SubscriptionPattern subscriptionPattern, ConsumerRebalanceListener consumerRebalanceListener) {
        runWithInactiveContext(() -> consumer.subscribe(subscriptionPattern, consumerRebalanceListener));
    }

    @Override
    public void subscribe(SubscriptionPattern subscriptionPattern) {
        runWithInactiveContext(() -> consumer.subscribe(subscriptionPattern));
    }

    @Override
    public void unsubscribe() {
        runWithInactiveContext(consumer::unsubscribe);
    }

    @Override
    public ConsumerRecords<K, V> poll(Duration duration) {
        return withInactiveContext(() -> traceConsumerRecords(consumer.poll(duration)));
    }

    private ConsumerRecords<K, V> traceConsumerRecords(ConsumerRecords<K, V> consumerRecords) {
        if (consumerRecords == null || consumerRecords.isEmpty()) {
            return consumerRecords;
        }
        Map<TopicPartition, List<ConsumerRecord<K, V>>> recordsByPartition = new LinkedHashMap<>();
        Set<ConsumerRecord<K, V>> tracedRecords = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TopicPartition topicPartition : consumerRecords.partitions()) {
            List<ConsumerRecord<K, V>> partitionRecords = consumerRecords.records(topicPartition);
            recordsByPartition.put(topicPartition, partitionRecords);
            for (ConsumerRecord<K, V> consumerRecord : partitionRecords) {
                if (!kafkaTelemetry.excludeTopic(consumerRecord.topic()) && kafkaTelemetry.filterConsumerRecord(consumerRecord, consumer)) {
                    tracedRecords.add(consumerRecord);
                }
            }
        }
        if (tracedRecords.isEmpty()) {
            return consumerRecords;
        }
        return new TracingConsumerRecords(recordsByPartition, tracedRecords);
    }

    @Override
    public void commitSync() {
        runWithInactiveContext(consumer::commitSync);
    }

    @Override
    public void commitSync(Duration duration) {
        runWithInactiveContext(() -> consumer.commitSync(duration));
    }

    @Override
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> map) {
        runWithInactiveContext(() -> consumer.commitSync(map));
    }

    @Override
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> map, Duration duration) {
        runWithInactiveContext(() -> consumer.commitSync(map, duration));
    }

    @Override
    public void commitAsync() {
        runWithInactiveContext(consumer::commitAsync);
    }

    @Override
    public void commitAsync(OffsetCommitCallback offsetCommitCallback) {
        runWithInactiveContext(() -> consumer.commitAsync(offsetCommitCallback));
    }

    @Override
    public void commitAsync(Map<TopicPartition, OffsetAndMetadata> map, OffsetCommitCallback offsetCommitCallback) {
        runWithInactiveContext(() -> consumer.commitAsync(map, offsetCommitCallback));
    }

    @Override
    public void registerMetricForSubscription(KafkaMetric kafkaMetric) {
        runWithInactiveContext(() -> consumer.registerMetricForSubscription(kafkaMetric));
    }

    @Override
    public void unregisterMetricFromSubscription(KafkaMetric kafkaMetric) {
        runWithInactiveContext(() -> consumer.unregisterMetricFromSubscription(kafkaMetric));
    }

    @Override
    public void seek(TopicPartition topicPartition, long l) {
        runWithInactiveContext(() -> consumer.seek(topicPartition, l));
    }

    @Override
    public void seek(TopicPartition topicPartition, OffsetAndMetadata offsetAndMetadata) {
        runWithInactiveContext(() -> consumer.seek(topicPartition, offsetAndMetadata));
    }

    @Override
    public void seekToBeginning(Collection<TopicPartition> collection) {
        runWithInactiveContext(() -> consumer.seekToBeginning(collection));
    }

    @Override
    public void seekToEnd(Collection<TopicPartition> collection) {
        runWithInactiveContext(() -> consumer.seekToEnd(collection));
    }

    @Override
    public long position(TopicPartition topicPartition) {
        return withInactiveContext(() -> consumer.position(topicPartition));
    }

    @Override
    public long position(TopicPartition topicPartition, Duration duration) {
        return withInactiveContext(() -> consumer.position(topicPartition, duration));
    }

    @Override
    public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> set) {
        return withInactiveContext(() -> consumer.committed(set));
    }

    @Override
    public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> set, Duration duration) {
        return withInactiveContext(() -> consumer.committed(set, duration));
    }

    @Override
    public Uuid clientInstanceId(Duration duration) {
        return withInactiveContext(() -> consumer.clientInstanceId(duration));
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return withInactiveContext(consumer::metrics);
    }

    @Override
    public List<PartitionInfo> partitionsFor(String s) {
        return withInactiveContext(() -> consumer.partitionsFor(s));
    }

    @Override
    public List<PartitionInfo> partitionsFor(String s, Duration duration) {
        return withInactiveContext(() -> consumer.partitionsFor(s, duration));
    }

    @Override
    public Map<String, List<PartitionInfo>> listTopics() {
        return withInactiveContext(consumer::listTopics);
    }

    @Override
    public Map<String, List<PartitionInfo>> listTopics(Duration duration) {
        return withInactiveContext(() -> consumer.listTopics(duration));
    }

    @Override
    public Set<TopicPartition> paused() {
        return withInactiveContext(consumer::paused);
    }

    @Override
    public void pause(Collection<TopicPartition> collection) {
        runWithInactiveContext(() -> consumer.pause(collection));
    }

    @Override
    public void resume(Collection<TopicPartition> collection) {
        runWithInactiveContext(() -> consumer.resume(collection));
    }

    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> map) {
        return withInactiveContext(() -> consumer.offsetsForTimes(map));
    }

    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> map, Duration duration) {
        return withInactiveContext(() -> consumer.offsetsForTimes(map, duration));
    }

    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> collection) {
        return withInactiveContext(() -> consumer.beginningOffsets(collection));
    }

    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> collection, Duration duration) {
        return withInactiveContext(() -> consumer.beginningOffsets(collection, duration));
    }

    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> collection) {
        return withInactiveContext(() -> consumer.endOffsets(collection));
    }

    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> collection, Duration duration) {
        return withInactiveContext(() -> consumer.endOffsets(collection, duration));
    }

    @Override
    public OptionalLong currentLag(TopicPartition topicPartition) {
        return withInactiveContext(() -> consumer.currentLag(topicPartition));
    }

    @Override
    public ConsumerGroupMetadata groupMetadata() {
        return withInactiveContext(consumer::groupMetadata);
    }

    @Override
    public void enforceRebalance() {
        runWithInactiveContext(consumer::enforceRebalance);
    }

    @Override
    public void enforceRebalance(String s) {
        runWithInactiveContext(() -> consumer.enforceRebalance(s));
    }

    @Override
    public void close() {
        runWithInactiveContext(consumer::close);
    }

    @Override
    public void close(Duration duration) {
        runWithInactiveContext(() -> consumer.close(CloseOptions.timeout(duration)));
    }

    @Override
    public void close(CloseOptions closeOptions) {
        runWithInactiveContext(() -> consumer.close(closeOptions));
    }

    @Override
    public void wakeup() {
        consumer.wakeup();
    }

    private void closeActiveRecordContext() {
        if (activeRecordContext == null) {
            return;
        }
        ActiveRecordContext current = activeRecordContext;
        activeRecordContext = null;
        current.scope.close();
        kafkaTelemetry.endConsumerRecordSpan(current.consumerRecordContext);
    }

    private <T> T withInactiveContext(Supplier<T> supplier) {
        closeActiveRecordContext();
        return supplier.get();
    }

    private void runWithInactiveContext(Runnable runnable) {
        closeActiveRecordContext();
        runnable.run();
    }

    private final class TracingConsumerRecords extends ConsumerRecords<K, V> {

        private final Set<ConsumerRecord<K, V>> tracedRecords;

        @SuppressWarnings("deprecation")
        private TracingConsumerRecords(Map<TopicPartition, List<ConsumerRecord<K, V>>> recordsByPartition, Set<ConsumerRecord<K, V>> tracedRecords) {
            super(recordsByPartition);
            this.tracedRecords = tracedRecords;
        }

        @Override
        public Iterator<ConsumerRecord<K, V>> iterator() {
            return instrumentedIterator(super.iterator());
        }

        @Override
        public List<ConsumerRecord<K, V>> records(TopicPartition partition) {
            return instrumentedRecords(super.records(partition));
        }

        @Override
        public Iterable<ConsumerRecord<K, V>> records(String topic) {
            return () -> instrumentedIterator(super.records(topic).iterator());
        }

        private List<ConsumerRecord<K, V>> instrumentedRecords(List<ConsumerRecord<K, V>> records) {
            return new AbstractList<>() {
                @Override
                public ConsumerRecord<K, V> get(int index) {
                    return activateNextRecord(records.get(index));
                }

                @Override
                public int size() {
                    return records.size();
                }

                @Override
                public Iterator<ConsumerRecord<K, V>> iterator() {
                    return instrumentedIterator(records.iterator());
                }

                @Override
                public ListIterator<ConsumerRecord<K, V>> listIterator(int index) {
                    return instrumentedListIterator(records.listIterator(index));
                }
            };
        }

        private Iterator<ConsumerRecord<K, V>> instrumentedIterator(Iterator<ConsumerRecord<K, V>> iterator) {
            closeActiveRecordContext();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return hasNextWithContext(iterator);
                }

                @Override
                public ConsumerRecord<K, V> next() {
                    return activateNextRecord(iterator.next());
                }
            };
        }

        private ListIterator<ConsumerRecord<K, V>> instrumentedListIterator(ListIterator<ConsumerRecord<K, V>> iterator) {
            closeActiveRecordContext();
            return new ListIterator<>() {
                @Override
                public boolean hasNext() {
                    return hasNextWithContext(iterator);
                }

                @Override
                public ConsumerRecord<K, V> next() {
                    return activateNextRecord(iterator.next());
                }

                @Override
                public boolean hasPrevious() {
                    boolean hasPrevious = iterator.hasPrevious();
                    if (!hasPrevious) {
                        closeActiveRecordContext();
                    }
                    return hasPrevious;
                }

                @Override
                public ConsumerRecord<K, V> previous() {
                    return activateNextRecord(iterator.previous());
                }

                @Override
                public int nextIndex() {
                    return iterator.nextIndex();
                }

                @Override
                public int previousIndex() {
                    return iterator.previousIndex();
                }

                @Override
                public void remove() {
                    iterator.remove();
                }

                @Override
                public void set(ConsumerRecord<K, V> consumerRecord) {
                    iterator.set(consumerRecord);
                }

                @Override
                public void add(ConsumerRecord<K, V> consumerRecord) {
                    iterator.add(consumerRecord);
                }
            };
        }

        private ConsumerRecord<K, V> activateNextRecord(ConsumerRecord<K, V> consumerRecord) {
            closeActiveRecordContext();
            if (tracedRecords.contains(consumerRecord)) {
                KafkaTelemetry.ConsumerRecordContext consumerRecordContext = kafkaTelemetry.startConsumerRecordSpan(consumerRecord, consumer);
                if (consumerRecordContext != null) {
                    activeRecordContext = new ActiveRecordContext(consumerRecordContext, consumerRecordContext.context().makeCurrent());
                }
            }
            return consumerRecord;
        }

        private boolean hasNextWithContext(Iterator<ConsumerRecord<K, V>> iterator) {
            boolean hasNext = iterator.hasNext();
            if (!hasNext) {
                closeActiveRecordContext();
            }
            return hasNext;
        }
    }

    private record ActiveRecordContext(KafkaTelemetry.ConsumerRecordContext consumerRecordContext, Scope scope) {
    }
}
