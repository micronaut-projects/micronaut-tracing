from typing import Annotated

from io.opentelemetry.api.common import AttributeKey
from io.opentelemetry.api.trace import SpanKind
from io.opentelemetry.sdk.testing.exporter import InMemorySpanExporter
from jakarta.inject import Inject
from micronaut.context.annotation import Property, Requires
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import HttpClient
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test


@Requires(property="spec.name", value="OrderedTracingServerFilterTest")
@Controller("/api")
class OrderedController:

    @Get("/ordered")
    def ordered(self) -> str:
        return "ordered"


@Property(name="spec.name", value="OrderedTracingServerFilterTest")
@MicronautTest
class OrderedTracingServerFilterTest:

    server: Annotated[EmbeddedServer, Inject]
    exporter: Annotated[InMemorySpanExporter, Inject]

    @BeforeEach
    def reset_exporter(self) -> None:
        self.exporter.reset()

    @Test
    def the_filter_tags_the_current_server_span(self) -> None:
        client = HttpClient.create(self.server.getURL())
        try:
            assert client.toBlocking().retrieve("/api/ordered") == "ordered"
        finally:
            client.close()

        server_spans = [span for span in self.exporter.getFinishedSpanItems() if span.getKind() == SpanKind.SERVER]
        assert len(server_spans) == 1
        span = server_spans[0]
        assert span.getName() == "GET /api/ordered"
        assert span.getAttributes().get(AttributeKey.stringKey("example.attribute")) == "value"
        assert span.getAttributes().get(AttributeKey.stringKey("example.response.attribute")) == "value"
