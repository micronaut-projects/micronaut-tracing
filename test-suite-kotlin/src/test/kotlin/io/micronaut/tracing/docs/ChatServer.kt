package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
// end::imports[]

@Requires(property = "spec.name", value = "ChatServerTest")
// tag::clazz[]
@ServerWebSocket("/chat/{topic}")
open class ChatServer {

    @OnOpen
    open fun onOpen(topic: String, session: WebSocketSession) { } // <1>

    @OnMessage
    open fun onMessage(topic: String, message: String, session: WebSocketSession) { } // <2>

    @OnClose
    open fun onClose(topic: String, session: WebSocketSession) { } // <3>
}
// end::clazz[]
