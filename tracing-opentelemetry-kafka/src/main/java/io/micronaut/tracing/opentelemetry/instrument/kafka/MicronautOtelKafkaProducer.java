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
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Producer wrapper for Open Telemetry instrumentation purposes.
 *
 * @param <K> key class
 * @param <V> value class
 */
@Internal
public class MicronautOtelKafkaProducer<K, V>  implements Producer<K, V> {
    private final Producer<K, V> producer;
    private final KafkaTelemetry kafkaTelemetry;

    MicronautOtelKafkaProducer(Producer<K, V> producer, KafkaTelemetry kafkaTelemetry) {
        this.producer = producer;
        this.kafkaTelemetry = kafkaTelemetry;
    }

    @Override
    public void initTransactions() {
        producer.initTransactions();
    }

    @Override
    public void beginTransaction() throws ProducerFencedException {
        producer.beginTransaction();
    }

    @Override
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> map, String s) throws ProducerFencedException {
        producer.sendOffsetsToTransaction(map, s);
    }

    @Override
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> map, ConsumerGroupMetadata consumerGroupMetadata) throws ProducerFencedException {
        producer.sendOffsetsToTransaction(map, consumerGroupMetadata);
    }

    @Override
    public void commitTransaction() throws ProducerFencedException {
        producer.commitTransaction();
    }

    @Override
    public void abortTransaction() throws ProducerFencedException {
        producer.abortTransaction();
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> producerRecord) {
        if (kafkaTelemetry.excludeTopic(producerRecord.topic()) || !kafkaTelemetry.filterProducerRecord(producerRecord, producer)) {
            return producer.send(producerRecord);
        }
        return kafkaTelemetry.buildAndInjectSpan(producerRecord, producer, null, producer::send);
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> producerRecord, Callback callback) {
        if (kafkaTelemetry.excludeTopic(producerRecord.topic()) || !kafkaTelemetry.filterProducerRecord(producerRecord, producer)) {
            return producer.send(producerRecord);
        }
        return kafkaTelemetry.buildAndInjectSpan(producerRecord, producer, callback, producer::send);
    }

    @Override
    public void flush() {
        producer.flush();
    }

    @Override
    public List<PartitionInfo> partitionsFor(String s) {
        return producer.partitionsFor(s);
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return producer.metrics();
    }

    @Override
    public void close() {
        producer.close();
    }

    @Override
    public void close(Duration duration) {
        producer.close(duration);
    }
}
