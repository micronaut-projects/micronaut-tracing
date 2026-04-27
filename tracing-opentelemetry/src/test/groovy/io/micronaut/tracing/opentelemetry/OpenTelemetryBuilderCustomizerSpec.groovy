package io.micronaut.tracing.opentelemetry

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.metrics.Aggregation
import io.opentelemetry.sdk.metrics.InstrumentSelector
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.View
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest(startApplication = false)
@Property(name = "spec.name", value = "OpenTelemetryBuilderCustomizerSpec")
class OpenTelemetryBuilderCustomizerSpec extends Specification {

    private static final String HISTOGRAM_NAME = "custom.bucket.histogram"
    private static final List<Double> HISTOGRAM_BOUNDARIES = [1.0d, 5.0d, 10.0d]

    @Inject
    OpenTelemetry openTelemetry

    @Inject
    InMemoryMetricReader metricReader

    void "builder customizer can register histogram views"() {
        given:
        def histogram = openTelemetry.getMeter("test").histogramBuilder(HISTOGRAM_NAME).build()

        when:
        histogram.record(0.5d)
        histogram.record(6.0d)
        def metric = metricReader.collectAllMetrics().find { it.name == HISTOGRAM_NAME }

        then:
        metric != null
        metric.histogramData.points.first().boundaries == HISTOGRAM_BOUNDARIES
        metric.histogramData.points.first().counts == [1L, 0L, 1L, 0L]
    }

    @Factory
    @Requires(property = "spec.name", value = "OpenTelemetryBuilderCustomizerSpec")
    static class CustomizerFactory {

        @Singleton
        InMemoryMetricReader metricReader() {
            InMemoryMetricReader.create()
        }

        @Singleton
        OpenTelemetryBuilderCustomizer histogramViewCustomizer(InMemoryMetricReader metricReader) {
            { builder -> builder.addMeterProviderCustomizer({ meterProviderBuilder, configProperties ->
                meterProviderBuilder.registerMetricReader(metricReader)
                meterProviderBuilder.registerView(
                    InstrumentSelector.builder()
                        .setType(InstrumentType.HISTOGRAM)
                        .setName(HISTOGRAM_NAME)
                        .build(),
                    View.builder()
                        .setAggregation(Aggregation.explicitBucketHistogram(HISTOGRAM_BOUNDARIES))
                        .build()
                )
                meterProviderBuilder
            }) } as OpenTelemetryBuilderCustomizer
        }
    }
}
