# tag::imports[]
from typing import Annotated

from io.opentelemetry.api import OpenTelemetry
from io.opentelemetry.api.common import AttributeKey
from io.opentelemetry.api.trace import SpanKind
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.tracing.opentelemetry.test import TestSpans
from org.junit.jupiter.api import Test

from .HelloService import HelloService
# end::imports[]


# tag::clazz[]
@MicronautTest  # <1>
class TestSpansExampleTest:

    hello_service: Annotated[HelloService, Inject]
    open_telemetry: Annotated[OpenTelemetry, Inject]
    spans: Annotated[TestSpans, Inject]  # <2>

    @Test
    def hello_creates_a_span(self) -> None:
        self.hello_service.hello("Fred")

        span = self.spans.spanNamed("HelloService.hello#hello-world")  # <3>
        assert span.getKind() == SpanKind.INTERNAL
        assert span.getAttributes().get(AttributeKey.stringKey("person.name")) == "Fred"

    @Test
    def hello_continues_the_current_trace(self) -> None:
        outer = self.open_telemetry.getTracer("test").spanBuilder("outer").startSpan()
        scope = outer.makeCurrent()
        try:
            self.hello_service.hello("Fred")
        finally:
            scope.close()
            outer.end()

        self.spans.awaitSpans(2)  # <4>
        assert self.spans.traces().size() == 1  # <5>
        parent = self.spans.parentOf(self.spans.spanNamed("HelloService.hello#hello-world")).get()
        assert parent.getName() == "outer"
# end::clazz[]
