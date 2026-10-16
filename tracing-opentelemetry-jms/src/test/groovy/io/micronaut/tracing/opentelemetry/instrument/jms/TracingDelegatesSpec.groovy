package io.micronaut.tracing.opentelemetry.instrument.jms

import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.jms.listener.JMSListener
import io.micronaut.jms.listener.JMSListenerRegistry
import io.micronaut.jms.model.JMSDestinationType
import io.micronaut.jms.pool.JMSConnectionPool
import io.micronaut.jms.pool.PooledObject
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.jms.BytesMessage
import jakarta.jms.CompletionListener
import jakarta.jms.Connection
import jakarta.jms.ConnectionConsumer
import jakarta.jms.ConnectionFactory
import jakarta.jms.ConnectionMetaData
import jakarta.jms.ExceptionListener
import jakarta.jms.JMSContext
import jakarta.jms.JMSException
import jakarta.jms.MapMessage
import jakarta.jms.Message
import jakarta.jms.MessageConsumer
import jakarta.jms.MessageListener
import jakarta.jms.MessageProducer
import jakarta.jms.ObjectMessage
import jakarta.jms.Queue
import jakarta.jms.QueueBrowser
import jakarta.jms.ServerSessionPool
import jakarta.jms.Session
import jakarta.jms.StreamMessage
import jakarta.jms.TemporaryQueue
import jakarta.jms.TemporaryTopic
import jakarta.jms.TextMessage
import jakarta.jms.Topic
import jakarta.jms.TopicSubscriber
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ExecutorService

/**
 * The tracing wrappers delegate to the wrapped JMS objects and trace the messages sent and processed.
 */
class TracingDelegatesSpec extends Specification {

    InMemorySpanExporter exporter = InMemorySpanExporter.create()

    @AutoCleanup
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
        .build()

    JmsTelemetry telemetry = new JmsTelemetry(openTelemetry)

    @Unroll
    void 'a synchronous #overload send is traced'() {
        given:
        MessageProducer delegate = Mock()
        Queue queue = Stub { getQueueName() >> 'orders' }
        delegate.getDestination() >> queue
        Message message = Mock()
        def producer = telemetry.wrap(delegate)

        when:
        send(producer, queue, message)

        then:
        1 * delegate.send(*_)
        exporter.finishedSpanItems.size() == 1
        with(exporter.finishedSpanItems[0]) {
            name == 'orders publish'
            kind == SpanKind.PRODUCER
            status.statusCode == StatusCode.UNSET
        }

        where:
        overload                   | send
        'message'                  | { p, d, m -> p.send(m) }
        'message with options'     | { p, d, m -> p.send(m, 1, 4, 0L) }
        'destination'              | { p, d, m -> p.send(d, m) }
        'destination with options' | { p, d, m -> p.send(d, m, 1, 4, 0L) }
    }

    @Unroll
    void 'an asynchronous #overload send is traced until its completion'() {
        given:
        CompletionListener completionListener = Mock()
        CompletionListener tracingListener = null
        MessageProducer delegate = Mock()
        Topic topic = Stub { getTopicName() >> 'news' }
        delegate.getDestination() >> topic
        delegate.send(*_) >> { List args -> tracingListener = args.last() }
        Message message = Mock()
        def producer = telemetry.wrap(delegate)

        when:
        send(producer, topic, message, completionListener)

        then:
        tracingListener != null
        !tracingListener.is(completionListener)
        exporter.finishedSpanItems.empty

        when:
        tracingListener.onCompletion(message)

        then:
        1 * completionListener.onCompletion(message)
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].name == 'news publish'
        exporter.finishedSpanItems[0].status.statusCode == StatusCode.UNSET

        where:
        overload                   | send
        'message'                  | { p, d, m, l -> p.send(m, l) }
        'message with options'     | { p, d, m, l -> p.send(m, 1, 4, 0L, l) }
        'destination'              | { p, d, m, l -> p.send(d, m, l) }
        'destination with options' | { p, d, m, l -> p.send(d, m, 1, 4, 0L, l) }
    }

    void 'an asynchronous send failing on completion records the exception'() {
        given:
        CompletionListener completionListener = Mock()
        CompletionListener tracingListener = null
        MessageProducer delegate = Mock()
        delegate.send(_ as Message, _ as CompletionListener) >> { Message m, CompletionListener l -> tracingListener = l }
        Message message = Mock()
        def exception = new JMSException('broker down')

        when:
        telemetry.wrap(delegate).send(message, completionListener)
        tracingListener.onException(message, exception)

        then:
        1 * completionListener.onException(message, exception)
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].status.statusCode == StatusCode.ERROR
        exporter.finishedSpanItems[0].events.any { it.name == 'exception' }
    }

    void 'an asynchronous send failing immediately ends the span'() {
        given:
        MessageProducer delegate = Mock()
        delegate.send(_ as Message, _ as CompletionListener) >> { throw new JMSException('rejected') }

        when:
        telemetry.wrap(delegate).send(Mock(Message), Mock(CompletionListener))

        then:
        thrown(JMSException)
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].status.statusCode == StatusCode.ERROR
    }

    void 'a failed synchronous send records the exception'() {
        given:
        MessageProducer delegate = Mock()
        delegate.getDestination() >> { throw new JMSException('closed') }
        delegate.send(_ as Message) >> { throw new IllegalStateException('closed') }

        when:
        telemetry.wrap(delegate).send(Mock(Message))

        then:
        thrown(IllegalStateException)
        exporter.finishedSpanItems.size() == 1
        with(exporter.finishedSpanItems[0]) {
            name == 'unknown publish'
            status.statusCode == StatusCode.ERROR
            events.any { it.name == 'exception' }
        }
    }

    void 'a send nested in a traced send is not traced again'() {
        given:
        MessageProducer inner = Mock()
        CompletionListener completionListener = Mock()
        MessageProducer outer = Mock()
        outer.send(_ as Message) >> { Message m ->
            def producer = telemetry.wrap(inner)
            producer.send(m)
            producer.send(m, completionListener)
        }

        when:
        telemetry.wrap(outer).send(Mock(Message))

        then:
        1 * inner.send(_ as Message)
        1 * inner.send(_ as Message, completionListener)
        exporter.finishedSpanItems.size() == 1
    }

    void 'the producer delegates its properties'() {
        given:
        MessageProducer delegate = Mock()
        Queue queue = Mock()
        delegate.getDisableMessageID() >> true
        delegate.getDisableMessageTimestamp() >> true
        delegate.getDeliveryMode() >> 2
        delegate.getPriority() >> 7
        delegate.getTimeToLive() >> 10L
        delegate.getDeliveryDelay() >> 20L
        delegate.getDestination() >> queue
        def producer = new TracingMessageProducer(delegate, telemetry)

        when:
        producer.disableMessageID = true
        producer.disableMessageTimestamp = true
        producer.deliveryMode = 2
        producer.priority = 7
        producer.timeToLive = 10L
        producer.deliveryDelay = 20L
        producer.close()

        then:
        1 * delegate.setDisableMessageID(true)
        1 * delegate.setDisableMessageTimestamp(true)
        1 * delegate.setDeliveryMode(2)
        1 * delegate.setPriority(7)
        1 * delegate.setTimeToLive(10L)
        1 * delegate.setDeliveryDelay(20L)
        1 * delegate.close()

        and:
        producer.disableMessageID
        producer.disableMessageTimestamp
        producer.deliveryMode == 2
        producer.priority == 7
        producer.timeToLive == 10L
        producer.deliveryDelay == 20L
        producer.destination.is(queue)
        producer.toString().startsWith('TracingMessageProducer(')
        telemetry.wrap(producer).is(producer)
    }

    void 'the session wraps its producers and delegates everything else'() {
        given:
        Session delegate = Mock()
        Queue queue = Mock()
        Topic topic = Mock()
        MessageProducer producer = Mock()
        MessageListener listener = Mock()
        MessageConsumer consumer = Mock()
        TopicSubscriber subscriber = Mock()
        QueueBrowser browser = Mock()
        BytesMessage bytes = Mock()
        MapMessage map = Mock()
        Message message = Mock()
        ObjectMessage object = Mock()
        StreamMessage stream = Mock()
        TextMessage text = Mock()
        TemporaryQueue temporaryQueue = Mock()
        TemporaryTopic temporaryTopic = Mock()
        delegate.createProducer(queue) >> producer
        delegate.createBytesMessage() >> bytes
        delegate.createMapMessage() >> map
        delegate.createMessage() >> message
        delegate.createObjectMessage() >> object
        delegate.createObjectMessage('payload') >> object
        delegate.createStreamMessage() >> stream
        delegate.createTextMessage() >> text
        delegate.createTextMessage('hi') >> text
        delegate.getTransacted() >> true
        delegate.getAcknowledgeMode() >> Session.CLIENT_ACKNOWLEDGE
        delegate.getMessageListener() >> listener
        delegate.createConsumer(queue) >> consumer
        delegate.createConsumer(queue, 'a = 1') >> consumer
        delegate.createConsumer(queue, 'a = 1', true) >> consumer
        delegate.createSharedConsumer(topic, 'shared') >> consumer
        delegate.createSharedConsumer(topic, 'shared', 'a = 1') >> consumer
        delegate.createQueue('q') >> queue
        delegate.createTopic('t') >> topic
        delegate.createDurableSubscriber(topic, 'durable') >> subscriber
        delegate.createDurableSubscriber(topic, 'durable', 'a = 1', true) >> subscriber
        delegate.createDurableConsumer(topic, 'durable') >> consumer
        delegate.createDurableConsumer(topic, 'durable', 'a = 1', true) >> consumer
        delegate.createSharedDurableConsumer(topic, 'durable') >> consumer
        delegate.createSharedDurableConsumer(topic, 'durable', 'a = 1') >> consumer
        delegate.createBrowser(queue) >> browser
        delegate.createBrowser(queue, 'a = 1') >> browser
        delegate.createTemporaryQueue() >> temporaryQueue
        delegate.createTemporaryTopic() >> temporaryTopic
        def session = telemetry.wrap(delegate)

        expect:
        session instanceof TracingSession
        telemetry.wrap(session).is(session)
        session.toString().startsWith('TracingSession(')
        session.createProducer(queue) instanceof TracingMessageProducer
        session.createBytesMessage().is(bytes)
        session.createMapMessage().is(map)
        session.createMessage().is(message)
        session.createObjectMessage().is(object)
        session.createObjectMessage('payload').is(object)
        session.createStreamMessage().is(stream)
        session.createTextMessage().is(text)
        session.createTextMessage('hi').is(text)
        session.transacted
        session.acknowledgeMode == Session.CLIENT_ACKNOWLEDGE
        session.messageListener.is(listener)
        session.createConsumer(queue).is(consumer)
        session.createConsumer(queue, 'a = 1').is(consumer)
        session.createConsumer(queue, 'a = 1', true).is(consumer)
        session.createSharedConsumer(topic, 'shared').is(consumer)
        session.createSharedConsumer(topic, 'shared', 'a = 1').is(consumer)
        session.createQueue('q').is(queue)
        session.createTopic('t').is(topic)
        session.createDurableSubscriber(topic, 'durable').is(subscriber)
        session.createDurableSubscriber(topic, 'durable', 'a = 1', true).is(subscriber)
        session.createDurableConsumer(topic, 'durable').is(consumer)
        session.createDurableConsumer(topic, 'durable', 'a = 1', true).is(consumer)
        session.createSharedDurableConsumer(topic, 'durable').is(consumer)
        session.createSharedDurableConsumer(topic, 'durable', 'a = 1').is(consumer)
        session.createBrowser(queue).is(browser)
        session.createBrowser(queue, 'a = 1').is(browser)
        session.createTemporaryQueue().is(temporaryQueue)
        session.createTemporaryTopic().is(temporaryTopic)
    }

    void 'the session delegates its lifecycle'() {
        given:
        Session delegate = Mock()
        MessageListener listener = Mock()
        def session = new TracingSession(delegate, telemetry)

        when:
        session.commit()
        session.rollback()
        session.recover()
        session.messageListener = listener
        session.run()
        session.unsubscribe('durable')
        session.close()

        then:
        1 * delegate.commit()
        1 * delegate.rollback()
        1 * delegate.recover()
        1 * delegate.setMessageListener(listener)
        1 * delegate.run()
        1 * delegate.unsubscribe('durable')
        1 * delegate.close()
    }

    void 'the connection wraps its sessions and delegates everything else'() {
        given:
        Connection delegate = Mock()
        Session session = Mock()
        ConnectionMetaData metaData = Mock()
        ExceptionListener exceptionListener = Mock()
        ConnectionConsumer consumer = Mock()
        ServerSessionPool sessionPool = Mock()
        Queue queue = Mock()
        Topic topic = Mock()
        delegate.createSession(true, Session.SESSION_TRANSACTED) >> session
        delegate.createSession(Session.AUTO_ACKNOWLEDGE) >> session
        delegate.createSession() >> session
        delegate.getClientID() >> 'client'
        delegate.getMetaData() >> metaData
        delegate.getExceptionListener() >> exceptionListener
        delegate.createConnectionConsumer(queue, 'a = 1', sessionPool, 5) >> consumer
        delegate.createSharedConnectionConsumer(topic, 'sub', 'a = 1', sessionPool, 5) >> consumer
        delegate.createDurableConnectionConsumer(topic, 'sub', 'a = 1', sessionPool, 5) >> consumer
        delegate.createSharedDurableConnectionConsumer(topic, 'sub', 'a = 1', sessionPool, 5) >> consumer
        def connection = telemetry.wrap(delegate)

        expect:
        connection instanceof TracingConnection
        telemetry.wrap(connection).is(connection)
        connection.toString().startsWith('TracingConnection(')
        connection.createSession(true, Session.SESSION_TRANSACTED) instanceof TracingSession
        connection.createSession(Session.AUTO_ACKNOWLEDGE) instanceof TracingSession
        connection.createSession() instanceof TracingSession
        connection.clientID == 'client'
        connection.metaData.is(metaData)
        connection.exceptionListener.is(exceptionListener)
        connection.createConnectionConsumer(queue, 'a = 1', sessionPool, 5).is(consumer)
        connection.createSharedConnectionConsumer(topic, 'sub', 'a = 1', sessionPool, 5).is(consumer)
        connection.createDurableConnectionConsumer(topic, 'sub', 'a = 1', sessionPool, 5).is(consumer)
        connection.createSharedDurableConnectionConsumer(topic, 'sub', 'a = 1', sessionPool, 5).is(consumer)
    }

    void 'the connection delegates its lifecycle'() {
        given:
        Connection delegate = Mock()
        ExceptionListener exceptionListener = Mock()
        def connection = new TracingConnection(delegate, telemetry)

        when:
        connection.clientID = 'client'
        connection.exceptionListener = exceptionListener
        connection.start()
        connection.stop()
        connection.close()

        then:
        1 * delegate.setClientID('client')
        1 * delegate.setExceptionListener(exceptionListener)
        1 * delegate.start()
        1 * delegate.stop()
        1 * delegate.close()
    }

    void 'the connection pool wraps its connections and delegates everything else'() {
        given:
        ConnectionFactory connectionFactory = Mock()
        JMSConnectionPool delegate = Mock()
        PooledObject<Connection> pooled = Mock()
        JMSContext jmsContext = Mock()
        delegate.getConnectionFactory() >> connectionFactory
        delegate.createConnection() >> Mock(Connection)
        delegate.createConnection('user', 'secret') >> Mock(Connection)
        delegate.request(_) >> pooled
        delegate.createContext() >> jmsContext
        delegate.createContext('user', 'secret') >> jmsContext
        delegate.createContext('user', 'secret', JMSContext.AUTO_ACKNOWLEDGE) >> jmsContext
        delegate.createContext(JMSContext.AUTO_ACKNOWLEDGE) >> jmsContext
        BeanCreatedEvent<JMSConnectionPool> event = Mock()
        event.getBean() >> delegate
        def instrumentation = new JmsConnectionPoolTracingInstrumentation(telemetry)

        when:
        def pool = instrumentation.onCreated(event)

        then:
        pool instanceof TracingJMSConnectionPool
        pool.toString().startsWith('TracingJMSConnectionPool(')
        pool.createConnection() instanceof TracingConnection
        pool.createConnection('user', 'secret') instanceof TracingConnection
        pool.request('arg').is(pooled)
        pool.createContext().is(jmsContext)
        pool.createContext('user', 'secret').is(jmsContext)
        pool.createContext('user', 'secret', JMSContext.AUTO_ACKNOWLEDGE).is(jmsContext)
        pool.createContext(JMSContext.AUTO_ACKNOWLEDGE).is(jmsContext)
        pool.connectionFactory.is(connectionFactory)

        when:
        pool.release(pooled)

        then:
        1 * delegate.release(pooled)

        when: 'an instrumented pool is not wrapped twice'
        BeanCreatedEvent<JMSConnectionPool> tracingEvent = Mock()
        tracingEvent.getBean() >> pool

        then:
        instrumentation.onCreated(tracingEvent).is(pool)
    }

    void 'the listener registry wraps the registered listeners'() {
        given:
        JMSListenerRegistry delegate = Mock()
        BeanCreatedEvent<JMSListenerRegistry> event = Mock()
        event.getBean() >> delegate
        def instrumentation = new JmsListenerRegistryTracingInstrumentation(telemetry)
        Connection connection = Mock()
        MessageListener listener = Mock()
        ExecutorService executor = Mock()
        JMSListener jmsListener = Mock()

        when:
        def registry = instrumentation.onCreated(event)

        then:
        registry instanceof TracingJMSListenerRegistry
        registry.toString().startsWith('TracingJMSListenerRegistry(')

        when:
        def registered = registry.register(connection, JMSDestinationType.QUEUE, 'orders', false,
            Session.AUTO_ACKNOWLEDGE, listener, executor, true, Optional.empty())

        then:
        1 * delegate.register(connection, JMSDestinationType.QUEUE, 'orders', false, Session.AUTO_ACKNOWLEDGE,
            { it instanceof TracingMessageListener }, executor, true, Optional.empty()) >> jmsListener
        registered.is(jmsListener)

        when:
        registry.register(jmsListener, true)
        registry.shutdown()

        then:
        1 * delegate.register(jmsListener, true)
        1 * delegate.shutdown()

        when: 'an instrumented registry is not wrapped twice'
        BeanCreatedEvent<JMSListenerRegistry> tracingEvent = Mock()
        tracingEvent.getBean() >> registry

        then:
        instrumentation.onCreated(tracingEvent).is(registry)
    }

    void 'a listener processes each message in a consumer span'() {
        given:
        MessageListener delegate = Mock()
        Message message = Mock()
        def listener = telemetry.wrap(delegate, 'orders')

        when:
        listener.onMessage(message)

        then:
        1 * delegate.onMessage(message)
        listener.toString().startsWith('TracingMessageListener(')
        telemetry.wrap(listener, 'orders').is(listener)
        exporter.finishedSpanItems.size() == 1
        with(exporter.finishedSpanItems[0]) {
            name == 'orders process'
            kind == SpanKind.CONSUMER
            status.statusCode == StatusCode.UNSET
        }
    }

    void 'an exception thrown by a listener is recorded on the consumer span'() {
        given:
        MessageListener delegate = Mock()
        delegate.onMessage(_) >> { throw new IllegalStateException('boom') }
        def listener = telemetry.wrap(delegate, 'orders')

        when:
        listener.onMessage(Mock(Message))

        then:
        thrown(IllegalStateException)
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].status.statusCode == StatusCode.ERROR
        exporter.finishedSpanItems[0].events.any { it.name == 'exception' }
    }
}
