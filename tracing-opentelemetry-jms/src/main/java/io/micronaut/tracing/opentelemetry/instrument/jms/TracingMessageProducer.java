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
import io.micronaut.core.annotation.Nullable;
import jakarta.jms.CompletionListener;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;

/**
 * A {@link MessageProducer} that sends each message in a {@code PRODUCER} span and writes the trace
 * context into the message properties.
 *
 * @since 8.4.0
 */
@Internal
final class TracingMessageProducer implements MessageProducer {

    private final MessageProducer delegate;
    private final JmsTelemetry telemetry;

    TracingMessageProducer(MessageProducer delegate, JmsTelemetry telemetry) {
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    @Override
    public void send(Message message) throws JMSException {
        telemetry.send(message, producerDestination(), () -> delegate.send(message));
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        telemetry.send(message, producerDestination(), () -> delegate.send(message, deliveryMode, priority, timeToLive));
    }

    @Override
    public void send(Destination destination, Message message) throws JMSException {
        telemetry.send(message, destination, () -> delegate.send(destination, message));
    }

    @Override
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        telemetry.send(message, destination, () -> delegate.send(destination, message, deliveryMode, priority, timeToLive));
    }

    @Override
    public void send(Message message, CompletionListener completionListener) throws JMSException {
        telemetry.sendAsync(message, producerDestination(), completionListener,
            listener -> delegate.send(message, listener));
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
        telemetry.sendAsync(message, producerDestination(), completionListener,
            listener -> delegate.send(message, deliveryMode, priority, timeToLive, listener));
    }

    @Override
    public void send(Destination destination, Message message, CompletionListener completionListener) throws JMSException {
        telemetry.sendAsync(message, destination, completionListener,
            listener -> delegate.send(destination, message, listener));
    }

    @Override
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
        telemetry.sendAsync(message, destination, completionListener,
            listener -> delegate.send(destination, message, deliveryMode, priority, timeToLive, listener));
    }

    @Nullable
    private Destination producerDestination() {
        try {
            return delegate.getDestination();
        } catch (JMSException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public void setDisableMessageID(boolean value) throws JMSException {
        delegate.setDisableMessageID(value);
    }

    @Override
    public boolean getDisableMessageID() throws JMSException {
        return delegate.getDisableMessageID();
    }

    @Override
    public void setDisableMessageTimestamp(boolean value) throws JMSException {
        delegate.setDisableMessageTimestamp(value);
    }

    @Override
    public boolean getDisableMessageTimestamp() throws JMSException {
        return delegate.getDisableMessageTimestamp();
    }

    @Override
    public void setDeliveryMode(int deliveryMode) throws JMSException {
        delegate.setDeliveryMode(deliveryMode);
    }

    @Override
    public int getDeliveryMode() throws JMSException {
        return delegate.getDeliveryMode();
    }

    @Override
    public void setPriority(int defaultPriority) throws JMSException {
        delegate.setPriority(defaultPriority);
    }

    @Override
    public int getPriority() throws JMSException {
        return delegate.getPriority();
    }

    @Override
    public void setTimeToLive(long timeToLive) throws JMSException {
        delegate.setTimeToLive(timeToLive);
    }

    @Override
    public long getTimeToLive() throws JMSException {
        return delegate.getTimeToLive();
    }

    @Override
    public void setDeliveryDelay(long deliveryDelay) throws JMSException {
        delegate.setDeliveryDelay(deliveryDelay);
    }

    @Override
    public long getDeliveryDelay() throws JMSException {
        return delegate.getDeliveryDelay();
    }

    @Override
    public Destination getDestination() throws JMSException {
        return delegate.getDestination();
    }

    @Override
    public void close() throws JMSException {
        delegate.close();
    }

    @Override
    public String toString() {
        return "TracingMessageProducer(" + delegate + ")";
    }
}
