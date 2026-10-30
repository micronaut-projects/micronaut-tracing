package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@Property(name = "spec.name", value = "ChatServerSpec")
@MicronautTest
class ChatServerSpec extends Specification {

    private static final AttributeKey<String> EVENT = AttributeKey.stringKey("micronaut.websocket.event")
    private static final AttributeKey<String> FUNCTION = AttributeKey.stringKey("code.function.name")

    @Inject
    @Client("/")
    WebSocketClient webSocketClient

    @Inject
    InMemorySpanExporter exporter

    void "the handlers of the server are traced"() {
        when:
        ChatClient client = Mono.from(webSocketClient.connect(ChatClient, "/chat/news")).block()
        client.send("hello")
        SpanData message = awaitSpan("MESSAGE /chat/{topic}")
        client.close()
        SpanData open = awaitSpan("OPEN /chat/{topic}")
        SpanData close = awaitSpan("CLOSE /chat/{topic}")
        SpanData upgrade = exporter.finishedSpanItems.find { it.kind == SpanKind.SERVER }

        then: "the span of @OnOpen is a child of the span of the HTTP upgrade request"
        open.kind == SpanKind.INTERNAL
        open.parentSpanId == upgrade.spanId
        open.attributes.get(EVENT) == "open"

        and: "the spans of @OnMessage and @OnClose start new traces, linked to the upgrade request"
        !message.parentSpanContext.valid
        message.links*.spanContext*.spanId == [upgrade.spanId]
        message.attributes.get(EVENT) == "message"
        !close.parentSpanContext.valid
        close.links*.spanContext*.spanId == [upgrade.spanId]
        close.attributes.get(EVENT) == "close"
    }

    private SpanData awaitSpan(String name) {
        new PollingConditions(timeout: 10).eventually {
            assert findSpan(name)
        }
        findSpan(name)
    }

    private SpanData findSpan(String name) {
        exporter.finishedSpanItems.find {
            it.name == name && it.attributes.get(FUNCTION)?.startsWith(ChatServer.name + ".")
        }
    }

    @Requires(property = "spec.name", value = "ChatServerSpec")
    @ClientWebSocket
    static abstract class ChatClient implements AutoCloseable {

        @OnMessage
        void onMessage(String message) {
        }

        abstract void send(String message)
    }
}
