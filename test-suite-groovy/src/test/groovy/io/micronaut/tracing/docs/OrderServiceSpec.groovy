package io.micronaut.tracing.docs

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class OrderServiceSpec extends Specification {

    @Inject
    OrderService orderService

    @Inject
    OpenTelemetry openTelemetry

    @Inject
    InMemorySpanExporter exporter

    void setup() {
        exporter.reset()
    }

    void "@AddingSpanAttributes adds the attribute to the current span"() {
        given:
        def outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan()

        when:
        String result
        try (def ignored = outer.makeCurrent()) {
            result = orderService.process("42")
        } finally {
            outer.end()
        }
        def spans = exporter.finishedSpanItems

        then:
        result == "processed 42"
        spans.size() == 1
        spans[0].name == "outer"
        spans[0].attributes.get(AttributeKey.stringKey("order.id")) == "42"
    }

    void "@AddingSpanAttributes creates no span"() {
        when:
        String result = orderService.process("42")

        then:
        result == "processed 42"
        exporter.finishedSpanItems.isEmpty()
    }
}
