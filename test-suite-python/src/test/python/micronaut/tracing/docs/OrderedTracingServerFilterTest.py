import time
from typing import Annotated

from io.opentelemetry.api.common import AttributeKey
from io.opentelemetry.api.trace import SpanKind
from io.opentelemetry.sdk.testing.exporter import InMemorySpanExporter
from jakarta.inject import Inject
from micronaut.context.annotation import Property, Requires
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test


@Controller("/api")
@Requires(property="spec.name", value="OrderedTracingServerFilterTest")
class HelloController:

    @Get("/hello")
    def hello(self) -> str:
        return "Hello"


@Property(name="spec.name", value="OrderedTracingServerFilterTest")
@MicronautTest
class OrderedTracingServerFilterTest:

    client: Annotated[HttpClient, Inject, Client("/")]
    exporter: Annotated[InMemorySpanExporter, Inject]

    @BeforeEach
    def reset_exporter(self) -> None:
        self.exporter.reset()

    @Test
    def the_filter_records_to_the_server_span_in_request_and_response_filters(self) -> None:
        assert self.client.toBlocking().retrieve("/api/hello") == "Hello"

        server_span = self._await_server_span()
        assert server_span.getName() == "GET /api/hello"
        assert server_span.getAttributes().get(AttributeKey.stringKey("example.attribute")) == "value"
        assert server_span.getAttributes().get(AttributeKey.stringKey("example.response.attribute")) == "value"

    def _await_server_span(self):
        # the server span is finished after the response filters ran, which may happen after the client got the response
        for _ in range(100):
            for span in self.exporter.getFinishedSpanItems():
                if span.getKind() == SpanKind.SERVER:
                    return span
            time.sleep(0.05)
        raise AssertionError("no server span was exported")
