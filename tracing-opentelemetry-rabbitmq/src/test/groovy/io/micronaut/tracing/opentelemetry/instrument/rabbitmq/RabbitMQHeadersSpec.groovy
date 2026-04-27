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
        expect:
        RabbitMQHeadersGetter.INSTANCE.keys([traceparent: "abc", retry: 3]).toSet() == ["traceparent", "retry"].toSet()
        RabbitMQHeadersGetter.INSTANCE.get([retry: 3], "retry") == "3"
        RabbitMQHeadersGetter.INSTANCE.get([retry: null], "retry") == null
        RabbitMQHeadersGetter.INSTANCE.get(null, "traceparent") == null
        !RabbitMQHeadersGetter.INSTANCE.keys(null).iterator().hasNext()
    }

    void "setter writes only when carrier is available"() {
        given:
        def headers = [:]

        when:
        RabbitMQHeadersSetter.INSTANCE.set(headers, "traceparent", "abc")
        RabbitMQHeadersSetter.INSTANCE.set(null, "ignored", "value")

        then:
        headers == [traceparent: "abc"]
    }
}
