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
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun theFilterRecordsToTheServerSpanInRequestAndResponseFilters() {
        assertEquals("Hello", client.toBlocking().retrieve("/api/hello"))

        val serverSpan = awaitServerSpan()
        assertEquals("GET /api/hello", serverSpan.name)
        assertEquals("value", serverSpan.attributes.get(AttributeKey.stringKey("example.attribute")))
        assertEquals("value", serverSpan.attributes.get(AttributeKey.stringKey("example.response.attribute")))
    }

    // the server span is finished after the response filters ran, which may happen after the client got the response
    private fun awaitServerSpan(): SpanData {
        var serverSpan: SpanData? = null
        var attempts = 0
        while (serverSpan == null && attempts++ < 100) {
            serverSpan = exporter.finishedSpanItems.firstOrNull { it.kind == SpanKind.SERVER }
            if (serverSpan == null) {
                Thread.sleep(50)
            }
        }
        assertNotNull(serverSpan, "no server span was exported")
        return serverSpan!!
    }

    @Controller("/api")
    @Requires(property = "spec.name", value = "OrderedTracingServerFilterTest")
    class HelloController {

        @Get("/hello")
        fun hello(): String = "Hello"
    }
}
