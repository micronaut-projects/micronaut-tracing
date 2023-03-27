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

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
final class KafkaBatchProcessSpanLinksExtractor implements SpanLinksExtractor<ConsumerRecords<?, ?>> {

    private final SpanLinksExtractor<ConsumerRecord<?, ?>> singleRecordLinkExtractor;

    KafkaBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
        singleRecordLinkExtractor = new PropagatorBasedSpanLinksExtractor<>(propagator, KafkaConsumerRecordGetter.INSTANCE);
    }

    @Override
    public void extract(SpanLinksBuilder spanLinks, Context parentContext, ConsumerRecords<?, ?> records) {

        for (ConsumerRecord<?, ?> record : records) {
            // explicitly passing root to avoid situation where context propagation is turned off and the
            // parent (CONSUMER receive) span is linked
            singleRecordLinkExtractor.extract(spanLinks, Context.root(), record);
        }
    }
}
