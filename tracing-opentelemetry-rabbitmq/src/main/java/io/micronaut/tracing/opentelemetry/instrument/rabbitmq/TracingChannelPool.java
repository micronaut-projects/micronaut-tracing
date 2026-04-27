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

import com.rabbitmq.client.Channel;
import io.micronaut.core.annotation.Internal;
import io.micronaut.rabbitmq.connect.ChannelPool;

import java.io.IOException;

/**
 * Tracing wrapper for {@link ChannelPool}.
 *
 * @since 8.0.0
 */
@Internal
final class TracingChannelPool implements ChannelPool {

    private final ChannelPool delegate;
    private final RabbitMQTelemetry telemetry;

    TracingChannelPool(ChannelPool delegate, RabbitMQTelemetry telemetry) {
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Channel getChannel() throws IOException {
        return telemetry.wrap(delegate.getChannel());
    }

    @Override
    public Channel getChannelWithRecoveringDelay(int recoveryAttempts) throws IOException, InterruptedException {
        return telemetry.wrap(delegate.getChannelWithRecoveringDelay(recoveryAttempts));
    }

    @Override
    public boolean isTopologyRecoveryEnabled() {
        return delegate.isTopologyRecoveryEnabled();
    }

    @Override
    public void returnChannel(Channel channel) {
        delegate.returnChannel(telemetry.unwrap(channel));
    }
}
