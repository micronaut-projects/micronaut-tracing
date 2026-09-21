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

    private val histogramBoundaries = listOf(1.0, 5.0, 10.0)

    @Singleton
    fun histogramViewCustomizer(): OpenTelemetryBuilderCustomizer {
        return OpenTelemetryBuilderCustomizer { builder ->
            builder.addMeterProviderCustomizer { meterProviderBuilder, _ ->
                meterProviderBuilder.registerView(
                    InstrumentSelector.builder()
                        .setType(InstrumentType.HISTOGRAM)
                        .setName("http.server.request.duration")
                        .build(),
                    View.builder()
                        .setAggregation(Aggregation.explicitBucketHistogram(histogramBoundaries))
                        .build()
                )
                meterProviderBuilder
            }
        }
    }
}
// end::histogramViewCustomizer[]
