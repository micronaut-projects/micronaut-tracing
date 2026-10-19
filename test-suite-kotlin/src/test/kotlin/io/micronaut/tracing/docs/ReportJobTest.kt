package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "ReportJobTest")
@MicronautTest(startApplication = false)
class ReportJobTest {

    @Inject
    lateinit var exporter: InMemorySpanExporter

    @Test
    fun eachRunOfTheScheduledMethodIsARootSpan() {
        val span = awaitSpan("ReportJob.generate")

        assertEquals(SpanKind.INTERNAL, span.kind)
        assertFalse(span.parentSpanContext.isValid)
        assertEquals(ReportJob::class.java.name + ".generate", span.attributes.get(AttributeKey.stringKey("code.function.name")))
        assertEquals("5m", span.attributes.get(AttributeKey.stringKey("micronaut.scheduled.fixed_delay")))
    }

    private fun awaitSpan(name: String): SpanData {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            exporter.finishedSpanItems.firstOrNull { it.name == name }?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("No span named $name")
    }
}
