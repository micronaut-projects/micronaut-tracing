from typing import Annotated

from io.opentelemetry.api.common import AttributeKey
from io.opentelemetry.api.trace import SpanKind
from io.opentelemetry.sdk.testing.exporter import InMemorySpanExporter
from jakarta.inject import Inject
from java.lang import System, Thread
from micronaut.context.annotation import Property, Requires
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.websocket import WebSocketClient, WebSocketSession
from micronaut.websocket.annotation import ClientWebSocket, OnMessage, OnOpen
from org.junit.jupiter.api import Test
from reactor.core.publisher import Mono

EVENT = AttributeKey.stringKey("micronaut.websocket.event")
FUNCTION = AttributeKey.stringKey("code.function.name")


@Requires(property="spec.name", value="ChatServerTest")
@ClientWebSocket
class ChatClient:

    session: WebSocketSession = None

    @OnOpen
    def on_open(self, session: WebSocketSession) -> None:
        self.session = session

    @OnMessage
    def on_message(self, message: str) -> None:
        pass


@Property(name="spec.name", value="ChatServerTest")
@MicronautTest
class ChatServerTest:

    web_socket_client: Annotated[WebSocketClient, Inject, Client("/")]
    exporter: Annotated[InMemorySpanExporter, Inject]

    @Test
    def the_handlers_of_the_server_are_traced(self) -> None:
        client = Mono.fromDirect(self.web_socket_client.connect(ChatClient, "/chat/news")).block()
        assert client.session is not None  # set by @OnOpen before the connection is emitted
        client.session.sendSync("hello")
        message = self.await_span("MESSAGE /chat/{topic}")
        client.session.close()
        open_span = self.await_span("OPEN /chat/{topic}")
        close = self.await_span("CLOSE /chat/{topic}")
        upgrade = None
        for span in self.exporter.getFinishedSpanItems():
            if span.getKind() == SpanKind.SERVER:
                upgrade = span

        # the span of @OnOpen is a child of the span of the HTTP upgrade request
        assert open_span.getKind() == SpanKind.INTERNAL
        assert open_span.getParentSpanId() == upgrade.getSpanId()
        assert open_span.getAttributes().get(EVENT) == "open"

        # the spans of @OnMessage and @OnClose start new traces, linked to the upgrade request
        assert not message.getParentSpanContext().isValid()
        assert message.getLinks().get(0).getSpanContext().getSpanId() == upgrade.getSpanId()
        assert message.getAttributes().get(EVENT) == "message"
        assert not close.getParentSpanContext().isValid()
        assert close.getLinks().get(0).getSpanContext().getSpanId() == upgrade.getSpanId()
        assert close.getAttributes().get(EVENT) == "close"

    def await_span(self, name: str):
        deadline = System.currentTimeMillis() + 10000
        while System.currentTimeMillis() < deadline:
            for span in self.exporter.getFinishedSpanItems():
                function_name = span.getAttributes().get(FUNCTION)
                if span.getName() == name and function_name is not None and "ChatServer." in function_name:
                    return span
            Thread.sleep(20)
        raise AssertionError("No span named " + name)
