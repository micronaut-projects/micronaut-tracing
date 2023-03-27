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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;

import static io.opentelemetry.api.common.AttributeKey.longKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
public final class KafkaConsumerExperimentalAttributesExtractor implements AttributesExtractor<ConsumerRecord<?, ?>, Void> {

    private static final AttributeKey<Long> KAFKA_RECORD_QUEUE_TIME_MS = longKey("kafka.record.queue_time_ms");

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, ConsumerRecord<?, ?> consumerRecord) {

        // don't record a duration if the message was sent from an old Kafka client
        if (consumerRecord.timestampType() != TimestampType.NO_TIMESTAMP_TYPE) {
            long produceTime = consumerRecord.timestamp();
            // this attribute shows how much time elapsed between the producer and the consumer of this
            // message, which can be helpful for identifying queue bottlenecks
            attributes.put(KAFKA_RECORD_QUEUE_TIME_MS, Math.max(0L, System.currentTimeMillis() - produceTime));
        }
    }

    @Override
    public void onEnd(AttributesBuilder attributes, Context context, ConsumerRecord<?, ?> consumerRecord, @Nullable Void unused, @Nullable Throwable error) {
    }
}
