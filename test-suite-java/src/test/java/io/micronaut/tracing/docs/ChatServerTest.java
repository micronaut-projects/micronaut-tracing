package io.micronaut.tracing.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Property(name = "spec.name", value = "ChatServerTest")
@MicronautTest
class ChatServerTest {

    private static final AttributeKey<String> EVENT = AttributeKey.stringKey("micronaut.websocket.event");
    private static final AttributeKey<String> FUNCTION = AttributeKey.stringKey("code.function.name");

    @Inject
    @Client("/")
    WebSocketClient webSocketClient;

    @Inject
    InMemorySpanExporter exporter;

    @Test
    void theHandlersOfTheServerAreTraced() throws Exception {
        ChatClient client = Mono.from(webSocketClient.connect(ChatClient.class, "/chat/news")).block();
        client.send("hello");
        SpanData message = awaitSpan("MESSAGE /chat/{topic}");
        client.close();

        SpanData open = awaitSpan("OPEN /chat/{topic}");
        SpanData close = awaitSpan("CLOSE /chat/{topic}");
        SpanData upgrade = exporter.getFinishedSpanItems().stream()
            .filter(span -> span.getKind() == SpanKind.SERVER)
            .findFirst()
            .orElseThrow();

        // the span of @OnOpen is a child of the span of the HTTP upgrade request
        assertEquals(SpanKind.INTERNAL, open.getKind());
        assertEquals(upgrade.getSpanId(), open.getParentSpanId());
        assertEquals("open", open.getAttributes().get(EVENT));

        // the spans of @OnMessage and @OnClose start new traces, linked to the upgrade request
        assertFalse(message.getParentSpanContext().isValid());
        assertEquals(upgrade.getSpanId(), message.getLinks().get(0).getSpanContext().getSpanId());
        assertEquals("message", message.getAttributes().get(EVENT));
        assertFalse(close.getParentSpanContext().isValid());
        assertEquals(upgrade.getSpanId(), close.getLinks().get(0).getSpanContext().getSpanId());
        assertEquals("close", close.getAttributes().get(EVENT));
    }

    private SpanData awaitSpan(String name) throws InterruptedException {
        String prefix = ChatServer.class.getName() + '.';
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<SpanData> span = exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name) && String.valueOf(s.getAttributes().get(FUNCTION)).startsWith(prefix))
                .findFirst();
            if (span.isPresent()) {
                return span.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("No span named " + name);
    }

    @Requires(property = "spec.name", value = "ChatServerTest")
    @ClientWebSocket
    abstract static class ChatClient implements AutoCloseable {

        @OnMessage
        void onMessage(String message) {
        }

        abstract void send(String message);
    }
}
