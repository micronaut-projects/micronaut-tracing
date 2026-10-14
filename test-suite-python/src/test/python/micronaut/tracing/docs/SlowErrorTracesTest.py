from typing import Annotated

from java.time import Duration, Instant

from micronaut.tracing.opentelemetry.inspector import TraceInspector
from io.opentelemetry.api import OpenTelemetry
from io.opentelemetry.api.trace import StatusCode
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test

from .SlowErrorTraces import SlowErrorTraces


# The inspector is only enabled by default in the dev environment
@Property(name="tracing.opentelemetry.inspector.enabled", value="true")
@MicronautTest(startApplication=False)
class SlowErrorTracesTest:

    open_telemetry: Annotated[OpenTelemetry, Inject]
    trace_inspector: Annotated[TraceInspector, Inject]
    slow_error_traces: Annotated[SlowErrorTraces, Inject]

    @BeforeEach
    def clear_traces(self) -> None:
        self.trace_inspector.clear()

    @Test
    def finds_the_slow_traces_with_an_error(self) -> None:
        self._end_span("slow-error", Duration.ofSeconds(1), True)
        self._end_span("fast-error", Duration.ofMillis(10), True)
        self._end_span("slow-ok", Duration.ofSeconds(1), False)

        traces = self.slow_error_traces.find()

        assert traces.size() == 1
        trace = traces.get(0)
        assert trace.name() == "slow-error"
        assert trace.error()
        assert trace.spanCount() == 1

    def _end_span(self, name: str, duration: Duration, error: bool) -> None:
        end = Instant.now()
        span = (
            self.open_telemetry.getTracer("test")
            .spanBuilder(name)
            .setNoParent()
            .setStartTimestamp(end.minus(duration))
            .startSpan()
        )
        if error:
            span.setStatus(StatusCode.ERROR)
        span.end(end)
