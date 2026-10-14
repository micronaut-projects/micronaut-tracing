package io.micronaut.tracing.docs

// tag::imports[]
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector
import io.micronaut.tracing.opentelemetry.inspector.TraceQuery
import io.micronaut.tracing.opentelemetry.inspector.TraceSummary
import jakarta.inject.Singleton
import java.time.Duration
// end::imports[]

// tag::clazz[]
@Singleton
class SlowErrorTraces(private val traceInspector: TraceInspector) { // <1>

    fun find(): List<TraceSummary> =
        traceInspector.traces(TraceQuery.builder() // <2>
            .errorsOnly(true)
            .minDuration(Duration.ofMillis(500))
            .limit(10)
            .build())
}
// end::clazz[]
