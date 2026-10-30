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
        session.sendSync("Joined " + topic)

    @OnMessage
    def on_message(self, topic: str, message: str, session: WebSocketSession) -> None:  # <2>
        session.sendSync(f"[{topic}] {message}")

    @OnClose
    def on_close(self, topic: str, session: WebSocketSession) -> None:  # <3>
        pass  # the session is closed: there is nothing left to send
# end::clazz[]
