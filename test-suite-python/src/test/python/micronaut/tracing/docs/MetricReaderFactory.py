from io.opentelemetry.sdk.testing.exporter import InMemoryMetricReader
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory, Requires
from micronaut.tracing.opentelemetry import OpenTelemetryBuilderCustomizer


@Factory
@Requires(property="spec.name", value="OpenTelemetryBuilderCustomizerExampleTest")
class MetricReaderFactory:
    """Registers an in-memory metric reader so the test can collect the recorded histogram."""

    @Singleton
    def metric_reader(self) -> InMemoryMetricReader:
        return InMemoryMetricReader.create()

    @Singleton
    def metric_reader_customizer(self, metric_reader: InMemoryMetricReader) -> OpenTelemetryBuilderCustomizer:
        return lambda builder: builder.addMeterProviderCustomizer(
            lambda meter_provider_builder, config_properties: meter_provider_builder.registerMetricReader(metric_reader)
        )
