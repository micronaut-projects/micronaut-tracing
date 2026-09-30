package io.micronaut.tracing.docs

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest
class HelloServiceTest {

    @Inject
    lateinit var helloService: HelloService

    @Inject
    lateinit var openTelemetry: OpenTelemetry

    @Inject
    lateinit var exporter: InMemorySpanExporter

    @BeforeEach
    fun resetExporter() {
        exporter.reset()
    }

    @Test
    fun newSpanCreatesASpanWithTheTaggedArgument() {
        assertEquals("Hello Fred", helloService.hello("Fred"))

        val spans = exporter.finishedSpanItems
        assertEquals(1, spans.size)
        val span = spans[0]
        assertEquals("HelloService.hello#hello-world", span.name)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertEquals("Fred", span.attributes.get(AttributeKey.stringKey("person.name")))
    }

    @Test
    fun continueSpanAddsTheTagOfTheNestedCallToTheSpan() {
        helloService.hello("Fred")

        val span = exporter.finishedSpanItems[0]
        assertEquals("Hello Fred", span.attributes.get(AttributeKey.stringKey("hello.greeting")))
    }

    @Test
    fun continueSpanTagsTheCurrentSpan() {
        val outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan()
        try {
            outer.makeCurrent().use {
                assertEquals("Hi", helloService.greet("Hi"))
            }
        } finally {
            outer.end()
        }

        val spans = exporter.finishedSpanItems
        assertEquals(1, spans.size)
        assertEquals("outer", spans[0].name)
        assertEquals("Hi", spans[0].attributes.get(AttributeKey.stringKey("hello.greeting")))
    }
}
