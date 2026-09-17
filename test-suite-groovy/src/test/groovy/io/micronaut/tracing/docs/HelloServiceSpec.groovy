package io.micronaut.tracing.docs

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class HelloServiceSpec extends Specification {

    @Inject
    HelloService helloService

    @Inject
    OpenTelemetry openTelemetry

    @Inject
    InMemorySpanExporter exporter

    void setup() {
        exporter.reset()
    }

    void "@NewSpan creates a span with the tagged argument"() {
        when:
        String greeting = helloService.hello("Fred")
        def spans = exporter.finishedSpanItems

        then:
        greeting == "Hello Fred"
        spans.size() == 1
        spans[0].name == "HelloService.hello#hello-world"
        spans[0].kind == SpanKind.INTERNAL
        spans[0].attributes.get(AttributeKey.stringKey("person.name")) == "Fred"
    }

    void "@ContinueSpan adds the tag of the nested call to the span"() {
        when:
        helloService.hello("Fred")

        then:
        exporter.finishedSpanItems[0].attributes.get(AttributeKey.stringKey("hello.greeting")) == "Hello Fred"
    }

    void "@ContinueSpan tags the current span"() {
        given:
        def outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan()

        when:
        String greeting
        try (def ignored = outer.makeCurrent()) {
            greeting = helloService.greet("Hi")
        } finally {
            outer.end()
        }
        def spans = exporter.finishedSpanItems

        then:
        greeting == "Hi"
        spans.size() == 1
        spans[0].name == "outer"
        spans[0].attributes.get(AttributeKey.stringKey("hello.greeting")) == "Hi"
    }
}
