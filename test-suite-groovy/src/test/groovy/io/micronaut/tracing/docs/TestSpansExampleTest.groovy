package io.micronaut.tracing.docs

// tag::imports[]
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.tracing.opentelemetry.test.TestSpans
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import jakarta.inject.Inject
import spock.lang.Specification
// end::imports[]

// tag::clazz[]
@MicronautTest // <1>
class TestSpansExampleTest extends Specification {

    @Inject
    HelloService helloService

    @Inject
    OpenTelemetry openTelemetry

    @Inject
    TestSpans spans // <2>

    void "hello creates a span"() {
        when:
        helloService.hello("Fred")
        def span = spans.spanNamed("HelloService.hello#hello-world") // <3>

        then:
        span.kind == SpanKind.INTERNAL
        span.attributes.get(AttributeKey.stringKey("person.name")) == "Fred"
    }

    void "hello continues the current trace"() {
        when:
        def outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan()
        def scope = outer.makeCurrent()
        try {
            helloService.hello("Fred")
        } finally {
            scope.close()
            outer.end()
        }
        spans.awaitSpans(2) // <4>

        then: // <5>
        spans.traces().size() == 1
        spans.parentOf(spans.spanNamed("HelloService.hello#hello-world")).get().name == "outer"
    }
}
// end::clazz[]
