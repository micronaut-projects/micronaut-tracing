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

import io.micronaut.core.annotation.NonNull;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Interface to filter producer messages for tracing.
 *
 * @param <K> key class
 * @param <V> value class
 *
 * @since 4.6.0
 */
public interface KafkaTelemetryProducerTracingFilter<K, V> {

    /**
     * Filter method for producer records, which should be traced.
     *
     * @param record the producer record
     * @param producer the producer
     * @return true if this producer record should be traced, false - otherwise.
     */
    boolean filter(@NonNull ProducerRecord<K, V> record, @NonNull Producer<K, V> producer);
}
