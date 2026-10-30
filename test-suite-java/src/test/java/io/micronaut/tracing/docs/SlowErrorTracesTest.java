package io.micronaut.tracing.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector;
import io.micronaut.tracing.opentelemetry.inspector.TraceSummary;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The inspector is only enabled by default in the dev environment
@Property(name = "tracing.opentelemetry.inspector.enabled", value = "true")
@MicronautTest(startApplication = false)
class SlowErrorTracesTest {

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    TraceInspector traceInspector;

    @Inject
    SlowErrorTraces slowErrorTraces;

    @BeforeEach
    void clearTraces() {
        traceInspector.clear();
    }

    @Test
    void findsTheSlowTracesWithAnError() {
        endSpan("slow-error", Duration.ofSeconds(1), true);
        endSpan("fast-error", Duration.ofMillis(10), true);
        endSpan("slow-ok", Duration.ofSeconds(1), false);

        List<TraceSummary> traces = slowErrorTraces.find();

        assertEquals(1, traces.size());
        TraceSummary trace = traces.get(0);
        assertEquals("slow-error", trace.name());
        assertTrue(trace.error());
        assertEquals(1, trace.spanCount());
    }

    private void endSpan(String name, Duration duration, boolean error) {
        Tracer tracer = openTelemetry.getTracer("test");
        Instant end = Instant.now();
        var span = tracer.spanBuilder(name).setNoParent().setStartTimestamp(end.minus(duration)).startSpan();
        if (error) {
            span.setStatus(StatusCode.ERROR);
        }
        span.end(end);
    }
}
