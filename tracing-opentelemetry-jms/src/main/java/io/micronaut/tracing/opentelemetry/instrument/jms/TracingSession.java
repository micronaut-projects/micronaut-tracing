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
import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TopicSubscriber;

import java.io.Serializable;

/**
 * A {@link Session} whose producers trace the messages they send.
 *
 * <p>Consumers are not wrapped: Micronaut JMS listeners are traced where they are registered with
 * the {@link io.micronaut.jms.listener.JMSListenerRegistry}, so that the span covers the listener
 * even when it runs on an executor.</p>
 *
 * @since 8.4.0
 */
@Internal
final class TracingSession implements Session {

    private final Session delegate;
    private final JmsTelemetry telemetry;

    TracingSession(Session delegate, JmsTelemetry telemetry) {
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    @Override
    public MessageProducer createProducer(Destination destination) throws JMSException {
        return telemetry.wrap(delegate.createProducer(destination));
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException {
        return delegate.createBytesMessage();
    }

    @Override
    public MapMessage createMapMessage() throws JMSException {
        return delegate.createMapMessage();
    }

    @Override
    public Message createMessage() throws JMSException {
        return delegate.createMessage();
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException {
        return delegate.createObjectMessage();
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
        return delegate.createObjectMessage(object);
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException {
        return delegate.createStreamMessage();
    }

    @Override
    public TextMessage createTextMessage() throws JMSException {
        return delegate.createTextMessage();
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException {
        return delegate.createTextMessage(text);
    }

    @Override
    public boolean getTransacted() throws JMSException {
        return delegate.getTransacted();
    }

    @Override
    public int getAcknowledgeMode() throws JMSException {
        return delegate.getAcknowledgeMode();
    }

    @Override
    public void commit() throws JMSException {
        delegate.commit();
    }

    @Override
    public void rollback() throws JMSException {
        delegate.rollback();
    }

    @Override
    public void close() throws JMSException {
        delegate.close();
    }

    @Override
    public void recover() throws JMSException {
        delegate.recover();
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return delegate.getMessageListener();
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        delegate.setMessageListener(listener);
    }

    @Override
    public void run() {
        delegate.run();
    }

    @Override
    public MessageConsumer createConsumer(Destination destination) throws JMSException {
        return delegate.createConsumer(destination);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException {
        return delegate.createConsumer(destination, messageSelector);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) throws JMSException {
        return delegate.createConsumer(destination, messageSelector, noLocal);
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
        return delegate.createSharedConsumer(topic, sharedSubscriptionName);
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) throws JMSException {
        return delegate.createSharedConsumer(topic, sharedSubscriptionName, messageSelector);
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        return delegate.createQueue(queueName);
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return delegate.createTopic(topicName);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
        return delegate.createDurableSubscriber(topic, name);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        return delegate.createDurableSubscriber(topic, name, messageSelector, noLocal);
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
        return delegate.createDurableConsumer(topic, name);
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        return delegate.createDurableConsumer(topic, name, messageSelector, noLocal);
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
        return delegate.createSharedDurableConsumer(topic, name);
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException {
        return delegate.createSharedDurableConsumer(topic, name, messageSelector);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        return delegate.createBrowser(queue);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
        return delegate.createBrowser(queue, messageSelector);
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        return delegate.createTemporaryQueue();
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        return delegate.createTemporaryTopic();
    }

    @Override
    public void unsubscribe(String name) throws JMSException {
        delegate.unsubscribe(name);
    }

    @Override
    public String toString() {
        return "TracingSession(" + delegate + ")";
    }
}
