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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.core.util.StringUtils;
import io.micronaut.rabbitmq.connect.ChannelPool;
import jakarta.inject.Singleton;

/**
 * Wraps RabbitMQ channel pools with tracing-aware channels.
 *
 * @since 8.4.0
 */
@Requires(property = RabbitMQTelemetryConfiguration.PREFIX + ".wrapper", notEquals = StringUtils.FALSE)
@Singleton
public class RabbitMQChannelPoolTracingInstrumentation implements BeanCreatedEventListener<ChannelPool>, BeanPreDestroyEventListener<ChannelPool> {

    private final RabbitMQTelemetry rabbitMQTelemetry;

    public RabbitMQChannelPoolTracingInstrumentation(RabbitMQTelemetry rabbitMQTelemetry) {
        this.rabbitMQTelemetry = rabbitMQTelemetry;
    }

    @Override
    public ChannelPool onCreated(BeanCreatedEvent<ChannelPool> event) {
        ChannelPool bean = event.getBean();
        if (bean instanceof TracingChannelPool) {
            return bean;
        }
        return rabbitMQTelemetry.wrap(bean);
    }

    /**
     * Hands the wrapped pool back for disposal: the channel pool bean of Micronaut RabbitMQ is the concrete
     * {@code DefaultChannelPool} type, and its {@code @PreDestroy} method is invoked on the disposed bean
     * cast to that type, which the tracing wrapper is not.
     *
     * @param event The bean pre-destroy event
     * @return The channel pool to dispose
     */
    @Override
    public ChannelPool onPreDestroy(BeanPreDestroyEvent<ChannelPool> event) {
        ChannelPool bean = event.getBean();
        if (bean instanceof TracingChannelPool tracingChannelPool) {
            return tracingChannelPool.getDelegate();
        }
        return bean;
    }
}
