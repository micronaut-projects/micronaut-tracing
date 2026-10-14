package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

// The InMemoryMetricReader is registered by micronaut-tracing-opentelemetry-test
@Property(name = "spec.name", value = "OpenTelemetryBuilderCustomizerExampleTest")
@MicronautTest(startApplication = false)
class OpenTelemetryBuilderCustomizerExampleTest {

    @Inject
    lateinit var openTelemetry: OpenTelemetry

    @Inject
    lateinit var metricReader: InMemoryMetricReader

    @Test
    fun theCustomizerRegistersTheHistogramView() {
        val histogram = openTelemetry.getMeter("test").histogramBuilder(HISTOGRAM_NAME).build()

        histogram.record(0.5)
        histogram.record(6.0)
        val metric = metricReader.collectAllMetrics().find { it.name == HISTOGRAM_NAME }

        assertNotNull(metric)
        val point = metric!!.histogramData.points.first()
        assertEquals(listOf(1.0, 5.0, 10.0), point.boundaries)
        assertEquals(listOf(1L, 0L, 1L, 0L), point.counts)
    }

    companion object {
        private const val HISTOGRAM_NAME = "http.server.request.duration"
    }
}
