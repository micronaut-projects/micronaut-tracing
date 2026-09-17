from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

try:
    from io.opentelemetry.api import OpenTelemetry
    from io.opentelemetry.sdk.testing.exporter import InMemoryMetricReader
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from opentelemetry.api import OpenTelemetry
    from opentelemetry.sdk.testing.exporter import InMemoryMetricReader

HISTOGRAM_NAME = "http.server.request.duration"


@Property(name="spec.name", value="OpenTelemetryBuilderCustomizerExampleTest")
@MicronautTest(startApplication=False)
class OpenTelemetryBuilderCustomizerExampleTest:

    open_telemetry: Annotated[OpenTelemetry, Inject]
    metric_reader: Annotated[InMemoryMetricReader, Inject]

    @Test
    def the_customizer_registers_the_histogram_view(self) -> None:
        histogram = self.open_telemetry.getMeter("test").histogramBuilder(HISTOGRAM_NAME).build()

        histogram.record(0.5)
        histogram.record(6.0)
        metric = None
        for data in self.metric_reader.collectAllMetrics():
            if data.getName() == HISTOGRAM_NAME:
                metric = data

        assert metric is not None
        point = metric.getHistogramData().getPoints().iterator().next()
        assert list(point.getBoundaries()) == [1.0, 5.0, 10.0]
        assert list(point.getCounts()) == [1, 0, 1, 0]
