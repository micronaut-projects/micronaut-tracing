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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Interface to filter consumer messages for tracing.
 *
 * @param <K> key class
 * @param <V> value class
 *
 * @since 4.6.0
 */
public interface KafkaTelemetryConsumerTracingFilter<K, V> {

    /**
     * Filter method for consumer records, which should be traced.
     *
     * @param record the consumer record
     * @param consumer the consumer
     * @return true if this consumer record should be traced, false - otherwise.
     */
    boolean filter(@NonNull ConsumerRecord<K, V> record, @NonNull Consumer<K, V> consumer);
}
