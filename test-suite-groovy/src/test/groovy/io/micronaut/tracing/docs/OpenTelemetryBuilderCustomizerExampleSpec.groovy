package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import jakarta.inject.Inject
import spock.lang.Specification

// The InMemoryMetricReader is registered by micronaut-tracing-opentelemetry-test
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
}
