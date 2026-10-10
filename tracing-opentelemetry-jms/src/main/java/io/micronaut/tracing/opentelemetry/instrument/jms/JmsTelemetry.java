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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Singleton;
import jakarta.jms.CompletionListener;
import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.jspecify.annotations.Nullable;

/**
 * Creates the OpenTelemetry spans of the messages sent and received through Micronaut JMS.
 *
 * <p>Sending a message creates a {@code PRODUCER} span ({@code publish}) and writes the trace
 * context into the message properties. Processing a message in a listener creates a
 * {@code CONSUMER} span ({@code process}) that continues the trace read from the message
 * properties, and that is current, also through Micronaut's {@link PropagatedContext}, while the
 * listener runs.</p>
 *
 * @since 8.4.0
 */
@Internal
@Singleton
final class JmsTelemetry {

    static final String INSTRUMENTATION_NAME = "io.micronaut.tracing.jms";
    static final String PUBLISH = "publish";
    static final String PROCESS = "process";

    private final Instrumenter<JmsRequest, Void> producerInstrumenter;
    private final Instrumenter<JmsRequest, Void> consumerInstrumenter;

    JmsTelemetry(OpenTelemetry openTelemetry) {
        JmsMessagingAttributesGetter getter = JmsMessagingAttributesGetter.INSTANCE;
        producerInstrumenter = Instrumenter.<JmsRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, MessagingOperationType.SEND, PUBLISH))
            .addAttributesExtractor(MessagingAttributesExtractor.create(getter, MessagingOperationType.SEND, PUBLISH))
            .buildProducerInstrumenter(JmsMessagePropertyAccessor.INSTANCE);
        consumerInstrumenter = Instrumenter.<JmsRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, MessagingOperationType.PROCESS, PROCESS))
            .addAttributesExtractor(MessagingAttributesExtractor.create(getter, MessagingOperationType.PROCESS, PROCESS))
            .buildConsumerInstrumenter(JmsMessagePropertyAccessor.INSTANCE);
    }

    /**
     * @param connection The connection
     * @return a connection whose sessions trace the messages sent through their producers
     */
    Connection wrap(Connection connection) {
        if (connection instanceof TracingConnection) {
            return connection;
        }
        return new TracingConnection(connection, this);
    }

    /**
     * @param session The session
     * @return a session whose producers trace the messages they send
     */
    Session wrap(Session session) {
        if (session instanceof TracingSession) {
            return session;
        }
        return new TracingSession(session, this);
    }

    /**
     * @param producer The producer
     * @return a producer that traces the messages it sends
     */
    MessageProducer wrap(MessageProducer producer) {
        if (producer instanceof TracingMessageProducer) {
            return producer;
        }
        return new TracingMessageProducer(producer, this);
    }

    /**
     * @param listener        The listener
     * @param destinationName The name of the destination the listener consumes
     * @return a listener that traces the processing of each message
     */
    MessageListener wrap(MessageListener listener, @Nullable String destinationName) {
        if (listener instanceof TracingMessageListener) {
            return listener;
        }
        return new TracingMessageListener(listener, destinationName, this);
    }

    /**
     * Sends a message in a {@code PRODUCER} span.
     *
     * @param message     The message
     * @param destination The destination
     * @param send        Sends the message
     * @throws JMSException if the message cannot be sent
     */
    void send(Message message, @Nullable Destination destination, Send send) throws JMSException {
        JmsRequest request = JmsRequest.of(message, destination);
        Context parentContext = Context.current();
        if (!producerInstrumenter.shouldStart(parentContext, request)) {
            send.send();
            return;
        }
        Context context = producerInstrumenter.start(parentContext, request);
        try (Scope ignored = context.makeCurrent()) {
            send.send();
        } catch (JMSException | RuntimeException | Error e) {
            producerInstrumenter.end(context, request, null, e);
            throw e;
        }
        producerInstrumenter.end(context, request, null, null);
    }

    /**
     * Sends a message asynchronously in a {@code PRODUCER} span ended on completion.
     *
     * @param message            The message
     * @param destination        The destination
     * @param completionListener The completion listener
     * @param send               Sends the message, notifying the given completion listener
     * @throws JMSException if the message cannot be sent
     */
    void sendAsync(Message message, @Nullable Destination destination, CompletionListener completionListener, AsyncSend send) throws JMSException {
        JmsRequest request = JmsRequest.of(message, destination);
        Context parentContext = Context.current();
        if (!producerInstrumenter.shouldStart(parentContext, request)) {
            send.send(completionListener);
            return;
        }
        Context context = producerInstrumenter.start(parentContext, request);
        CompletionListener tracingListener = new CompletionListener() {
            @Override
            public void onCompletion(Message sent) {
                producerInstrumenter.end(context, request, null, null);
                try (Scope ignored = parentContext.makeCurrent()) {
                    completionListener.onCompletion(sent);
                }
            }

            @Override
            public void onException(Message failed, Exception exception) {
                producerInstrumenter.end(context, request, null, exception);
                try (Scope ignored = parentContext.makeCurrent()) {
                    completionListener.onException(failed, exception);
                }
            }
        };
        try (Scope ignored = context.makeCurrent()) {
            send.send(tracingListener);
        } catch (JMSException | RuntimeException | Error e) {
            producerInstrumenter.end(context, request, null, e);
            throw e;
        }
    }

    /**
     * Processes a received message in a {@code CONSUMER} span.
     *
     * @param listener        The listener processing the message
     * @param message         The message
     * @param destinationName The name of the destination the message was received from
     */
    void process(MessageListener listener, Message message, @Nullable String destinationName) {
        JmsRequest request = JmsRequest.of(message, destinationName);
        // the parent is read from the message: the listener thread carries no trace of its own
        Context parentContext = Context.root();
        if (!consumerInstrumenter.shouldStart(parentContext, request)) {
            listener.onMessage(message);
            return;
        }
        Context context = consumerInstrumenter.start(parentContext, request);
        // Also expose the context through Micronaut's propagated context, so that work the listener
        // hands off to a context-propagating executor or a reactive pipeline continues the trace.
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(context));
        try (Scope ignored = context.makeCurrent()) {
            propagatedContext.propagate(() -> listener.onMessage(message));
        } catch (RuntimeException | Error e) {
            consumerInstrumenter.end(context, request, null, e);
            throw e;
        }
        consumerInstrumenter.end(context, request, null, null);
    }

    /**
     * Sends a message.
     */
    @FunctionalInterface
    interface Send {
        void send() throws JMSException;
    }

    /**
     * Sends a message asynchronously.
     */
    @FunctionalInterface
    interface AsyncSend {
        void send(CompletionListener completionListener) throws JMSException;
    }
}
