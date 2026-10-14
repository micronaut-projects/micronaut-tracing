package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "OrderedTracingServerFilterTest")
@MicronautTest
class OrderedTracingServerFilterTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var exporter: InMemorySpanExporter

    @BeforeEach
    fun resetExporter() {
        exporter.reset()
    }

    @Test
    fun theFilterTagsTheCurrentServerSpan() {
        assertEquals("ordered", client.toBlocking().retrieve("/api/ordered"))

        val serverSpans = exporter.finishedSpanItems.filter { it.kind == SpanKind.SERVER }
        assertEquals(1, serverSpans.size)
        val span = serverSpans[0]
        assertEquals("GET /api/ordered", span.name)
        assertEquals("value", span.attributes.get(AttributeKey.stringKey("example.attribute")))
        assertEquals("value", span.attributes.get(AttributeKey.stringKey("example.response.attribute")))
    }

    @Requires(property = "spec.name", value = "OrderedTracingServerFilterTest")
    @Controller("/api")
    class OrderedController {

        @Get("/ordered")
        fun ordered(): String = "ordered"
    }
}
