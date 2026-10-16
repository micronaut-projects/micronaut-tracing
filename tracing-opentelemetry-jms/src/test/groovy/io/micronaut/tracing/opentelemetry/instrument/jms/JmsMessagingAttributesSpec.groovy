package io.micronaut.tracing.opentelemetry.instrument.jms

import jakarta.jms.Destination
import jakarta.jms.JMSException
import jakarta.jms.Message
import jakarta.jms.Queue
import jakarta.jms.TemporaryQueue
import jakarta.jms.TemporaryTopic
import jakarta.jms.Topic
import spock.lang.Specification

/**
 * The messaging attributes and the trace context are read from the JMS messages without failing.
 */
class JmsMessagingAttributesSpec extends Specification {

    JmsMessagingAttributesGetter getter = JmsMessagingAttributesGetter.INSTANCE
    JmsMessagePropertyAccessor accessor = JmsMessagePropertyAccessor.INSTANCE

    void 'the messaging attributes are read from the request'() {
        given:
        Message message = Stub {
            getJMSCorrelationID() >> 'correlation'
            getJMSMessageID() >> 'ID:1'
        }
        TemporaryQueue queue = Stub { getQueueName() >> 'temp' }
        JmsRequest request = JmsRequest.of(message, (Destination) queue)

        expect:
        getter.getSystem(request) == 'jms'
        getter.getDestination(request) == 'temp'
        getter.getDestinationTemplate(request) == null
        getter.isTemporaryDestination(request)
        !getter.isAnonymousDestination(request)
        getter.getConversationId(request) == 'correlation'
        getter.getMessageBodySize(request) == null
        getter.getMessageEnvelopeSize(request) == null
        getter.getMessageId(request, null) == 'ID:1'
        getter.getClientId(request) == null
        getter.getBatchMessageCount(request, null) == null
    }

    void 'the message identifiers are skipped when the message cannot provide them'() {
        given:
        Message message = Stub {
            getJMSCorrelationID() >> { throw new JMSException('closed') }
            getJMSMessageID() >> { throw new IllegalStateException('closed') }
        }
        JmsRequest request = JmsRequest.of(message, 'orders')

        expect:
        getter.getConversationId(request) == null
        getter.getMessageId(request, null) == null
        getter.getDestination(request) == 'orders'
        !getter.isTemporaryDestination(request)
    }

    void 'the destination of a request is resolved from the JMS destination'() {
        given:
        Destination destination = destinations[kind]

        expect:
        JmsRequest.of(Mock(Message), destination).destinationName() == name
        JmsRequest.of(Mock(Message), destination).temporaryDestination() == temporary

        where:
        kind             | name     | temporary
        'none'           | null     | false
        'destination'    | null     | false
        'queue'          | 'orders' | false
        'topic'          | 'news'   | false
        'temporaryTopic' | 'temp'   | true
        'failing'        | null     | false
    }

    private Map<String, Destination> getDestinations() {
        [
            none          : null,
            destination   : Stub(Destination),
            queue         : Stub(Queue) { getQueueName() >> 'orders' },
            topic         : Stub(Topic) { getTopicName() >> 'news' },
            temporaryTopic: Stub(TemporaryTopic) { getTopicName() >> 'temp' },
            failing       : Stub(Queue) { getQueueName() >> { throw new JMSException('closed') } },
        ]
    }

    void 'the trace context is not propagated without a carrier'() {
        expect:
        accessor.get(null, 'traceparent') == null

        when:
        accessor.set(null, 'traceparent', 'value')

        then:
        noExceptionThrown()
    }

    void 'the property names of a message are read as propagation keys'() {
        given:
        Message message = Stub {
            getPropertyNames() >> Collections.enumeration(['traceparent', 'X__dash__B3__dash__TraceId', 42])
        }

        expect:
        accessor.keys(JmsRequest.of(message, 'orders')).toList() == ['traceparent', 'X-B3-TraceId']
    }

    void 'no propagation keys are read when the message has no property names'() {
        given:
        Message withoutNames = Stub { getPropertyNames() >> null }
        Message failing = Stub { getPropertyNames() >> { throw new JMSException('closed') } }

        expect:
        accessor.keys(JmsRequest.of(withoutNames, 'orders')).toList().empty
        accessor.keys(JmsRequest.of(failing, 'orders')).toList().empty
    }

    void 'a property that cannot be read is skipped'() {
        given:
        Message message = Stub {
            getObjectProperty('traceparent') >> { throw new JMSException('closed') }
            getObjectProperty('tracestate') >> 1
        }
        JmsRequest request = JmsRequest.of(message, 'orders')

        expect:
        accessor.get(request, 'traceparent') == null
        accessor.get(request, 'tracestate') == '1'
    }

    void 'a property that cannot be written is skipped'() {
        given:
        Message message = Mock()
        message.setStringProperty(_, _) >> { throw new JMSException('read-only') }

        when:
        accessor.set(JmsRequest.of(message, 'orders'), 'traceparent', 'value')

        then:
        noExceptionThrown()
    }

    void 'the instrumentation is enabled by default and can be disabled'() {
        given:
        def configuration = new JmsTelemetryConfiguration()

        expect:
        configuration.enabled
        JmsTelemetryConfiguration.PREFIX == 'otel.instrumentation.jms'

        when:
        configuration.enabled = false

        then:
        !configuration.enabled
    }
}
