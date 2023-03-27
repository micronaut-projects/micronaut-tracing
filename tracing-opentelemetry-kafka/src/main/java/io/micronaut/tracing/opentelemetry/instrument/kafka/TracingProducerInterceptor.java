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

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * Default tracing producer kafka listener.
 *
 * @param <K> key class
 * @param <V> value class
 *
 * @since 4.6.0
 */
public class TracingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> producerRecord) {

        KafkaTelemetry kafkaTelemetry = KafkaTelemetryConfig.getKafkaTelemetry();
        if (kafkaTelemetry.excludeTopic(producerRecord.topic())) {
            return producerRecord;
        }
        if (!filterRecord(producerRecord)) {
            return producerRecord;
        }

        KafkaTelemetryConfig.getKafkaTelemetry().buildAndFinishSpan(producerRecord, null, null);
        return producerRecord;
    }

    /**
     * Override this method if you need to set custom condition or logic to filter message to trace.
     *
     * @param record consumer record
     *
     * @return true if this record need to trace, false - otherwise
     */
    public boolean filterRecord(ProducerRecord<K, V> record) {
        return true;
    }

    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> map) {
    }
}
