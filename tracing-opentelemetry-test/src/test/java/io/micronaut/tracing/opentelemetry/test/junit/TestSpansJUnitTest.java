package io.micronaut.tracing.opentelemetry.test.junit;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.test.TestSpans;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestSpansJUnitTest {

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    TestSpans spans;

    @Test
    @Order(1)
    void recordsSpans() {
        Tracer tracer = openTelemetry.getTracer("test");
        Span parent = tracer.spanBuilder("parent").startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            tracer.spanBuilder("child").startSpan().end();
        } finally {
            parent.end();
        }

        assertEquals(2, spans.awaitSpans(2).size());
        spans.assertTraces().hasTracesSatisfyingExactly(trace -> trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span -> span.hasName("child").hasParent(spans.spanNamed("parent"))
        ));
    }

    @Test
    @Order(2)
    void spansAreResetBeforeEachTest() {
        assertTrue(spans.finishedSpans().isEmpty());
    }
}
