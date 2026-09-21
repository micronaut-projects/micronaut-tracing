package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@Property(name = "spec.name", value = "OpenTelemetryBuilderCustomizerExampleSpec")
@MicronautTest(startApplication = false)
class OpenTelemetryBuilderCustomizerExampleSpec extends Specification {

    private static final String HISTOGRAM_NAME = "http.server.request.duration"

    @Inject
    OpenTelemetry openTelemetry

    @Inject
    InMemoryMetricReader metricReader

    void "the customizer registers the histogram view"() {
        given:
        def histogram = openTelemetry.getMeter("test").histogramBuilder(HISTOGRAM_NAME).build()

        when:
        histogram.record(0.5d)
        histogram.record(6.0d)
        def metric = metricReader.collectAllMetrics().find { it.name == HISTOGRAM_NAME }

        then:
        metric != null
        metric.histogramData.points.first().boundaries == [1.0d, 5.0d, 10.0d]
        metric.histogramData.points.first().counts == [1L, 0L, 1L, 0L]
    }

    @Factory
    @Requires(property = "spec.name", value = "OpenTelemetryBuilderCustomizerExampleSpec")
    static class MetricReaderFactory {

        @Singleton
        InMemoryMetricReader metricReader() {
            InMemoryMetricReader.create()
        }

        @Singleton
        OpenTelemetryBuilderCustomizer metricReaderCustomizer(InMemoryMetricReader metricReader) {
            return { builder ->
                builder.addMeterProviderCustomizer { meterProviderBuilder, configProperties ->
                    meterProviderBuilder.registerMetricReader(metricReader)
                }
            } as OpenTelemetryBuilderCustomizer
        }
    }
}
