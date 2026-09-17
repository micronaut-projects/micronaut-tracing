# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory
from micronaut.tracing.opentelemetry import OpenTelemetryBuilderCustomizer

try:
    from io.opentelemetry.sdk.metrics import Aggregation, InstrumentSelector, InstrumentType, View
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from opentelemetry.sdk.metrics import Aggregation, InstrumentSelector, InstrumentType, View
# end::imports[]

# tag::histogramViewCustomizer[]
HISTOGRAM_BOUNDARIES = [1.0, 5.0, 10.0]


@Factory
class OpenTelemetryBuilderCustomizerExample:

    @Singleton
    def histogram_view_customizer(self) -> OpenTelemetryBuilderCustomizer:
        def register_histogram_view(meter_provider_builder, config_properties):
            meter_provider_builder.registerView(
                InstrumentSelector.builder()
                    .setType(InstrumentType.HISTOGRAM)
                    .setName("http.server.request.duration")
                    .build(),
                View.builder()
                    .setAggregation(Aggregation.explicitBucketHistogram(HISTOGRAM_BOUNDARIES))
                    .build()
            )
            return meter_provider_builder

        return lambda builder: builder.addMeterProviderCustomizer(register_histogram_view)
# end::histogramViewCustomizer[]
