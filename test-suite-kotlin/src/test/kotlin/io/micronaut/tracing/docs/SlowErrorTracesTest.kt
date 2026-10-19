package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

// The inspector is only enabled by default in the dev environment
@Property(name = "tracing.opentelemetry.inspector.enabled", value = "true")
@MicronautTest(startApplication = false)
class SlowErrorTracesTest {

    @Inject
    lateinit var openTelemetry: OpenTelemetry

    @Inject
    lateinit var traceInspector: TraceInspector

    @Inject
    lateinit var slowErrorTraces: SlowErrorTraces

    @BeforeEach
    fun clearTraces() {
        traceInspector.clear()
    }

    @Test
    fun findsTheSlowTracesWithAnError() {
        endSpan("slow-error", Duration.ofSeconds(1), true)
        endSpan("fast-error", Duration.ofMillis(10), true)
        endSpan("slow-ok", Duration.ofSeconds(1), false)

        val traces = slowErrorTraces.find()

        assertEquals(1, traces.size)
        assertEquals("slow-error", traces[0].name())
        assertTrue(traces[0].error())
        assertEquals(1, traces[0].spanCount())
    }

    private fun endSpan(name: String, duration: Duration, error: Boolean) {
        val end = Instant.now()
        val span = openTelemetry.getTracer("test").spanBuilder(name)
            .setNoParent()
            .setStartTimestamp(end.minus(duration))
            .startSpan()
        if (error) {
            span.setStatus(StatusCode.ERROR)
        }
        span.end(end)
    }
}
