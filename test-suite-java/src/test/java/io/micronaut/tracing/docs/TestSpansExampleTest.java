package io.micronaut.tracing.docs;

// tag::imports[]
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.test.TestSpans;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
// end::imports[]

// tag::clazz[]
@MicronautTest // <1>
class TestSpansExampleTest {

    @Inject
    HelloService helloService;

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    TestSpans spans; // <2>

    @Test
    void helloCreatesASpan() {
        helloService.hello("Fred");

        assertThat(spans.spanNamed("HelloService.hello#hello-world")) // <3>
            .hasKind(SpanKind.INTERNAL)
            .hasAttribute(stringKey("person.name"), "Fred");
    }

    @Test
    void helloContinuesTheCurrentTrace() {
        Span outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan();
        try (Scope ignored = outer.makeCurrent()) {
            helloService.hello("Fred");
        } finally {
            outer.end();
        }

        spans.awaitSpans(2); // <4>
        spans.assertTraces().hasTracesSatisfyingExactly(trace -> trace.hasSpansSatisfyingExactly( // <5>
            span -> span.hasName("outer").hasNoParent(),
            span -> span.hasName("HelloService.hello#hello-world").hasParent(spans.spanNamed("outer"))
        ));
    }
}
// end::clazz[]
