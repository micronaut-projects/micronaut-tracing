package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

import spock.lang.Specification

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
        getter.get([retry: 3], "retry") == "3"
        getter.get([retry: null], "retry") == null
        getter.get(null, "traceparent") == null
        !getter.keys(null).iterator().hasNext()
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
