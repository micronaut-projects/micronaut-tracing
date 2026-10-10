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

import io.micronaut.core.annotation.Internal;
import io.micronaut.jms.listener.JMSListener;
import io.micronaut.jms.listener.JMSListenerRegistry;
import io.micronaut.jms.model.JMSDestinationType;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageListener;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * A {@link JMSListenerRegistry} that traces the listeners registered with it.
 *
 * <p>Every operation is delegated to the wrapped registry, which keeps the listeners and the global
 * success and error handlers. The message listener of each registration (for {@code @JMSListener}
 * beans, the listener that binds the message and invokes the annotated method) is wrapped so that
 * it processes each message in a {@code CONSUMER} span. The listener runs inside the span also when
 * the registration uses an executor.</p>
 *
 * @since 8.4.0
 */
@Internal
final class TracingJMSListenerRegistry extends JMSListenerRegistry {

    private final JMSListenerRegistry delegate;
    private final JmsTelemetry telemetry;

    TracingJMSListenerRegistry(JMSListenerRegistry delegate, JmsTelemetry telemetry) {
        super(Collections.emptyList(), Collections.emptyList());
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    @Override
    public void register(JMSListener listener, boolean autoStart) throws JMSException {
        delegate.register(listener, autoStart);
    }

    @Override
    @SuppressWarnings("java:S107") // mirrors the overridden method
    public JMSListener register(Connection connection,
                                JMSDestinationType destinationType,
                                String destination,
                                boolean transacted,
                                int acknowledgeMode,
                                MessageListener delegateListener,
                                ExecutorService executor,
                                boolean autoStart,
                                Optional<String> messageSelector) throws JMSException {
        return delegate.register(connection, destinationType, destination, transacted, acknowledgeMode,
            telemetry.wrap(delegateListener, destination), executor, autoStart, messageSelector);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public String toString() {
        return "TracingJMSListenerRegistry(" + delegate + ")";
    }
}
