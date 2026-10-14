from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.websocket import WebSocketSession
from micronaut.websocket.annotation import OnClose, OnMessage, OnOpen, ServerWebSocket
# end::imports[]


@Requires(property="spec.name", value="ChatServerTest")
# tag::clazz[]
@ServerWebSocket("/chat/{topic}")
class ChatServer:

    @OnOpen
    def on_open(self, topic: str, session: WebSocketSession) -> None:  # <1>
        pass

    @OnMessage
    def on_message(self, topic: str, message: str, session: WebSocketSession) -> None:  # <2>
        pass

    @OnClose
    def on_close(self, topic: str, session: WebSocketSession) -> None:  # <3>
        pass
# end::clazz[]
