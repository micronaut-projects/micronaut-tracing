from typing import Annotated

from io.opentelemetry.api.common import AttributeKey
from io.opentelemetry.api.trace import SpanKind
from io.opentelemetry.sdk.testing.exporter import InMemorySpanExporter
from jakarta.inject import Inject
from java.lang import System, Thread
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="ReportJobTest")
@MicronautTest(startApplication=False)
class ReportJobTest:

    exporter: Annotated[InMemorySpanExporter, Inject]

    @Test
    def each_run_of_the_scheduled_method_is_a_root_span(self) -> None:
        span = self.await_span("ReportJob.generate")

        assert span.getKind() == SpanKind.INTERNAL
        assert not span.getParentSpanContext().isValid()
        assert span.getAttributes().get(AttributeKey.stringKey("code.function.name")).endswith("ReportJob.generate")
        assert span.getAttributes().get(AttributeKey.stringKey("micronaut.scheduled.fixed_delay")) == "5m"

    def await_span(self, name: str):
        deadline = System.currentTimeMillis() + 10000
        while System.currentTimeMillis() < deadline:
            for span in self.exporter.getFinishedSpanItems():
                if span.getName() == name:
                    return span
            Thread.sleep(20)
        raise AssertionError("No span named " + name)
