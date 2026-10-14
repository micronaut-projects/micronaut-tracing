# tag::imports[]
from java.time import Duration

from jakarta.inject import Singleton
from micronaut.tracing.opentelemetry.inspector import TraceInspector, TraceQuery, TraceSummary
# end::imports[]


# tag::clazz[]
@Singleton
class SlowErrorTraces:

    def __init__(self, trace_inspector: TraceInspector) -> None:  # <1>
        self._trace_inspector = trace_inspector

    def find(self) -> list[TraceSummary]:
        return self._trace_inspector.traces(  # <2>
            TraceQuery.builder()
            .errorsOnly(True)
            .minDuration(Duration.ofMillis(500))
            .limit(10)
            .build()
        )
# end::clazz[]
