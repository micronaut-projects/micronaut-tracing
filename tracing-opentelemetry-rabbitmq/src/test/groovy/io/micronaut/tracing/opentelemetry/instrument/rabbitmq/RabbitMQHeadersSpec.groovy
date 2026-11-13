package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

import com.rabbitmq.client.LongString
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class RabbitMQHeadersSpec extends Specification {

    void "configuration defaults wrapper instrumentation on"() {
        given:
        def configuration = new RabbitMQTelemetryConfiguration()

        expect:
        configuration.wrapper

        when:
        configuration.wrapper = false

        then:
        !configuration.wrapper
    }

    void "getter reads carrier keys and string values"() {
        given:
        def getter = new RabbitMQHeadersGetter()

        expect:
        getter.keys([traceparent: "abc", retry: 3]).toSet() == ["traceparent", "retry"].toSet()
        getter.get([retry: 3], "retry") == null
        getter.get([retry: null], "retry") == null
        getter.get(null, "traceparent") == null
        !getter.keys(null).iterator().hasNext()
    }

    void "getter decodes AMQP long string and byte headers as UTF-8"() {
        given:
        def getter = new RabbitMQHeadersGetter()
        def longString = Stub(LongString) {
            length() >> 55
            getBytes() >> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".getBytes(StandardCharsets.UTF_8)
        }

        expect:
        getter.get([traceparent: longString], "traceparent") == "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        getter.get([traceparent: "abc".getBytes(StandardCharsets.UTF_8)], "traceparent") == "abc"
    }

    void "getter ignores oversized propagation header values"() {
        given:
        def getter = new RabbitMQHeadersGetter()
        def longString = Mock(LongString)

        when:
        def decoded = getter.get([traceparent: longString], "traceparent")

        then:
        1 * longString.length() >> RabbitMQHeadersGetter.MAX_PROPAGATION_HEADER_VALUE_BYTES + 1
        0 * longString.getBytes()
        decoded == null
        getter.get([traceparent: new byte[RabbitMQHeadersGetter.MAX_PROPAGATION_HEADER_VALUE_BYTES + 1]], "traceparent") == null
        getter.get([traceparent: "a" * (RabbitMQHeadersGetter.MAX_PROPAGATION_HEADER_VALUE_BYTES + 1)], "traceparent") == null
    }

    void "setter writes only when carrier is available"() {
        given:
        def headers = [:]
        def setter = new RabbitMQHeadersSetter()

        when:
        setter.set(headers, "traceparent", "abc")
        setter.set(null, "ignored", "value")

        then:
        headers == [traceparent: "abc"]
    }
}
