package io.micronaut.tracing.docs

// tag::imports[]
import io.micronaut.context.annotation.Factory
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer
import io.opentelemetry.sdk.metrics.Aggregation
import io.opentelemetry.sdk.metrics.InstrumentSelector
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.View
import jakarta.inject.Singleton
// end::imports[]

// tag::histogramViewCustomizer[]
@Factory
class OpenTelemetryBuilderCustomizerExample {

    private static final List<Double> HISTOGRAM_BOUNDARIES = [1.0d, 5.0d, 10.0d]

    @Singleton
    OpenTelemetryBuilderCustomizer histogramViewCustomizer() {
        return { builder ->
            builder.addMeterProviderCustomizer { meterProviderBuilder, configProperties ->
                meterProviderBuilder.registerView(
                    InstrumentSelector.builder()
                        .setType(InstrumentType.HISTOGRAM)
                        .setName("http.server.request.duration")
                        .build(),
                    View.builder()
                        .setAggregation(Aggregation.explicitBucketHistogram(HISTOGRAM_BOUNDARIES))
                        .build()
                )
                meterProviderBuilder
            }
        } as OpenTelemetryBuilderCustomizer
    }
}
// end::histogramViewCustomizer[]
