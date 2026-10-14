package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

@Property(name = "spec.name", value = "ChatServerTest")
@MicronautTest
class ChatServerTest {

    @Inject
    @field:Client("/")
    lateinit var webSocketClient: WebSocketClient

    @Inject
    lateinit var exporter: InMemorySpanExporter

    @Test
    fun theHandlersOfTheServerAreTraced() {
        val client = Mono.from(webSocketClient.connect(ChatClient::class.java, "/chat/news")).block()!!
        client.send("hello")
        val message = awaitSpan("MESSAGE /chat/{topic}")
        client.close()
        val open = awaitSpan("OPEN /chat/{topic}")
        val close = awaitSpan("CLOSE /chat/{topic}")
        val upgrade = exporter.finishedSpanItems.first { it.kind == SpanKind.SERVER }

        // the span of @OnOpen is a child of the span of the HTTP upgrade request
        assertEquals(SpanKind.INTERNAL, open.kind)
        assertEquals(upgrade.spanId, open.parentSpanId)
        assertEquals("open", open.attributes.get(EVENT))

        // the spans of @OnMessage and @OnClose start new traces, linked to the upgrade request
        assertFalse(message.parentSpanContext.isValid)
        assertEquals(listOf(upgrade.spanId), message.links.map { it.spanContext.spanId })
        assertEquals("message", message.attributes.get(EVENT))
        assertFalse(close.parentSpanContext.isValid)
        assertEquals(listOf(upgrade.spanId), close.links.map { it.spanContext.spanId })
        assertEquals("close", close.attributes.get(EVENT))
    }

    private fun awaitSpan(name: String): SpanData {
        val prefix = ChatServer::class.java.name + "."
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            exporter.finishedSpanItems
                .firstOrNull { it.name == name && it.attributes.get(FUNCTION)?.startsWith(prefix) == true }
                ?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("No span named $name")
    }

    @Requires(property = "spec.name", value = "ChatServerTest")
    @ClientWebSocket
    abstract class ChatClient : AutoCloseable {

        @OnMessage
        open fun onMessage(message: String) {
        }

        abstract fun send(message: String)
    }

    companion object {
        private val EVENT: AttributeKey<String> = AttributeKey.stringKey("micronaut.websocket.event")
        private val FUNCTION: AttributeKey<String> = AttributeKey.stringKey("code.function.name")
    }
}
