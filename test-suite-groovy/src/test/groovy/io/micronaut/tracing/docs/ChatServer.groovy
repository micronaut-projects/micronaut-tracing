package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
// end::imports[]

@Requires(property = "spec.name", value = "ChatServerSpec")
// tag::clazz[]
@ServerWebSocket("/chat/{topic}")
class ChatServer {

    @OnOpen
    void onOpen(String topic, WebSocketSession session) { } // <1>

    @OnMessage
    void onMessage(String topic, String message, WebSocketSession session) { } // <2>

    @OnClose
    void onClose(String topic, WebSocketSession session) { } // <3>
}
// end::clazz[]
