package io.micronaut.tracing.docs

// tag::imports[]
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.tracing.opentelemetry.test.TestSpans
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
// end::imports[]

// tag::clazz[]
@MicronautTest // <1>
class TestSpansExampleTest {

    @Inject
    lateinit var helloService: HelloService

    @Inject
    lateinit var openTelemetry: OpenTelemetry

    @Inject
    lateinit var spans: TestSpans // <2>

    @Test
    fun helloCreatesASpan() {
        helloService.hello("Fred")

        assertThat(spans.spanNamed("HelloService.hello#hello-world")) // <3>
            .hasKind(SpanKind.INTERNAL)
            .hasAttribute(stringKey("person.name"), "Fred")
    }

    @Test
    fun helloContinuesTheCurrentTrace() {
        val outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan()
        try {
            outer.makeCurrent().use {
                helloService.hello("Fred")
            }
        } finally {
            outer.end()
        }

        spans.awaitSpans(2) // <4>
        spans.assertTraces().hasTracesSatisfyingExactly({ trace -> // <5>
            trace.hasSpansSatisfyingExactly(
                { span -> span.hasName("outer").hasNoParent() },
                { span -> span.hasName("HelloService.hello#hello-world").hasParent(spans.spanNamed("outer")) }
            )
        })
    }
}
// end::clazz[]
