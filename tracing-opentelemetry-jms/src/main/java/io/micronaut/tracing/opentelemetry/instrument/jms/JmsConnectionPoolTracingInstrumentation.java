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
package io.micronaut.tracing.opentelemetry.instrument.jms;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;

/**
 * Wraps every {@link JMSConnectionPool} so that the messages sent through it, by
 * {@code @JMSProducer} clients, {@code JmsProducer} templates or code of your own, are traced.
 *
 * @since 8.4.0
 */
@Internal
@Singleton
final class JmsConnectionPoolTracingInstrumentation implements BeanCreatedEventListener<JMSConnectionPool> {

    private final JmsTelemetry telemetry;

    JmsConnectionPoolTracingInstrumentation(JmsTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public JMSConnectionPool onCreated(BeanCreatedEvent<JMSConnectionPool> event) {
        JMSConnectionPool pool = event.getBean();
        if (pool instanceof TracingJMSConnectionPool) {
            return pool;
        }
        return new TracingJMSConnectionPool(pool, telemetry);
    }
}
