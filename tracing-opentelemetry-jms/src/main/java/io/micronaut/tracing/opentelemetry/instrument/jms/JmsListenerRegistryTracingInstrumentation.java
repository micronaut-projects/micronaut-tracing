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
import io.micronaut.jms.listener.JMSListenerRegistry;
import jakarta.inject.Singleton;

/**
 * Wraps the {@link JMSListenerRegistry} so that the messages processed by {@code @JMSListener}
 * methods, and by listeners registered with the registry directly, are traced.
 *
 * @since 8.4.0
 */
@Internal
@Singleton
final class JmsListenerRegistryTracingInstrumentation implements BeanCreatedEventListener<JMSListenerRegistry> {

    private final JmsTelemetry telemetry;

    JmsListenerRegistryTracingInstrumentation(JmsTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public JMSListenerRegistry onCreated(BeanCreatedEvent<JMSListenerRegistry> event) {
        JMSListenerRegistry registry = event.getBean();
        if (registry instanceof TracingJMSListenerRegistry) {
            return registry;
        }
        return new TracingJMSListenerRegistry(registry, telemetry);
    }
}
