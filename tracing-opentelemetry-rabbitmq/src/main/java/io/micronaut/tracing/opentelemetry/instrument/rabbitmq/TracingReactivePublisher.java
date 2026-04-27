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
package io.micronaut.tracing.opentelemetry.instrument.rabbitmq;

import io.micronaut.core.annotation.Internal;
import io.micronaut.rabbitmq.bind.RabbitConsumerState;
import io.micronaut.rabbitmq.reactive.RabbitPublishState;
import io.micronaut.rabbitmq.reactive.ReactivePublisher;
import org.reactivestreams.Publisher;

/**
 * Tracing wrapper for {@link ReactivePublisher}.
 *
 * @since 8.0.0
 */
@Internal
final class TracingReactivePublisher implements ReactivePublisher {

    private final ReactivePublisher delegate;
    private final RabbitMQTelemetry telemetry;

    TracingReactivePublisher(ReactivePublisher delegate, RabbitMQTelemetry telemetry) {
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    @Override
    public Publisher<Void> publishAndConfirm(RabbitPublishState publishState) {
        return telemetry.tracePublish(publishState, delegate::publishAndConfirm);
    }

    @Override
    public Publisher<Void> publish(RabbitPublishState publishState) {
        return telemetry.tracePublish(publishState, delegate::publish);
    }

    @Override
    public Publisher<RabbitConsumerState> publishAndReply(RabbitPublishState publishState) {
        return telemetry.tracePublishAndReply(publishState, delegate::publishAndReply);
    }
}
