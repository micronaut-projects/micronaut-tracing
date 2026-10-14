package io.micronaut.tracing.docs

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest
class OrderServiceTest {

    @Inject
    lateinit var orderService: OrderService

    @Inject
    lateinit var openTelemetry: OpenTelemetry

    @Inject
    lateinit var exporter: InMemorySpanExporter

    @BeforeEach
    fun resetExporter() {
        exporter.reset()
    }

    @Test
    fun addingSpanAttributesAddsTheAttributeToTheCurrentSpan() {
        val outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan()
        try {
            outer.makeCurrent().use {
                assertEquals("processed 42", orderService.process("42"))
            }
        } finally {
            outer.end()
        }

        val spans = exporter.finishedSpanItems
        assertEquals(1, spans.size)
        assertEquals("outer", spans[0].name)
        assertEquals("42", spans[0].attributes.get(AttributeKey.stringKey("order.id")))
    }

    @Test
    fun addingSpanAttributesCreatesNoSpan() {
        assertEquals("processed 42", orderService.process("42"))

        assertTrue(exporter.finishedSpanItems.isEmpty())
    }
}
