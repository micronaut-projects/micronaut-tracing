package io.micronaut.tracing.opentelemetry.instrument.jms

import org.apache.activemq.command.ActiveMQTextMessage
import spock.lang.Specification

class JmsMessagePropertyAccessorSpec extends Specification {

    JmsMessagePropertyAccessor accessor = JmsMessagePropertyAccessor.INSTANCE

    void "propagation fields are stored as JMS properties with dashes replaced"() {
        given:
        ActiveMQTextMessage message = new ActiveMQTextMessage()
        JmsRequest request = JmsRequest.of(message, "queue")

        when:
        accessor.set(request, "traceparent", "00-trace-span-01")
        accessor.set(request, "X-B3-TraceId", "abc")

        then:
        message.getStringProperty("traceparent") == "00-trace-span-01"
        message.getStringProperty("X__dash__B3__dash__TraceId") == "abc"
        accessor.get(request, "traceparent") == "00-trace-span-01"
        accessor.get(request, "X-B3-TraceId") == "abc"
        accessor.get(request, "missing") == null
        accessor.keys(request).toSet() == ["traceparent", "X-B3-TraceId"] as Set
    }

    void "a read-only message is left without the trace context"() {
        given:
        ActiveMQTextMessage message = new ActiveMQTextMessage()
        message.setReadOnlyProperties(true)

        when:
        accessor.set(JmsRequest.of(message, "queue"), "traceparent", "00-trace-span-01")

        then:
        noExceptionThrown()
        message.getStringProperty("traceparent") == null
    }
}
