from typing import Annotated

from io.opentelemetry.api import OpenTelemetry
from io.opentelemetry.api.common import AttributeKey
from io.opentelemetry.sdk.testing.exporter import InMemorySpanExporter
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test

from .OrderService import OrderService


@MicronautTest
class OrderServiceTest:

    order_service: Annotated[OrderService, Inject]
    open_telemetry: Annotated[OpenTelemetry, Inject]
    exporter: Annotated[InMemorySpanExporter, Inject]

    @BeforeEach
    def reset_exporter(self) -> None:
        self.exporter.reset()

    @Test
    def adding_span_attributes_adds_the_attribute_to_the_current_span(self) -> None:
        outer = self.open_telemetry.getTracer("test").spanBuilder("outer").startSpan()
        scope = outer.makeCurrent()
        try:
            assert self.order_service.process("42") == "processed 42"
        finally:
            scope.close()
            outer.end()

        spans = self.exporter.getFinishedSpanItems()
        assert spans.size() == 1
        assert spans.get(0).getName() == "outer"
        assert spans.get(0).getAttributes().get(AttributeKey.stringKey("order.id")) == "42"

    @Test
    def adding_span_attributes_creates_no_span(self) -> None:
        assert self.order_service.process("42") == "processed 42"

        assert self.exporter.getFinishedSpanItems().isEmpty()
