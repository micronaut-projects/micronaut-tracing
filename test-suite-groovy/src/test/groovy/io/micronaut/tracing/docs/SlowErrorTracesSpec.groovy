package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

// The inspector is only enabled by default in the dev environment
@Property(name = "tracing.opentelemetry.inspector.enabled", value = "true")
@MicronautTest(startApplication = false)
class SlowErrorTracesSpec extends Specification {

    @Inject
    OpenTelemetry openTelemetry

    @Inject
    TraceInspector traceInspector

    @Inject
    SlowErrorTraces slowErrorTraces

    void setup() {
        traceInspector.clear()
    }

    void "finds the slow traces with an error"() {
        given:
        endSpan("slow-error", Duration.ofSeconds(1), true)
        endSpan("fast-error", Duration.ofMillis(10), true)
        endSpan("slow-ok", Duration.ofSeconds(1), false)

        when:
        def traces = slowErrorTraces.find()

        then:
        traces.size() == 1
        traces[0].name() == "slow-error"
        traces[0].error()
        traces[0].spanCount() == 1
    }

    private void endSpan(String name, Duration duration, boolean error) {
        Instant end = Instant.now()
        def span = openTelemetry.getTracer("test").spanBuilder(name)
            .setNoParent()
            .setStartTimestamp(end - duration)
            .startSpan()
        if (error) {
            span.setStatus(StatusCode.ERROR)
        }
        span.end(end)
    }
}
