package io.micronaut.tracing.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// The InMemoryMetricReader is registered by micronaut-tracing-opentelemetry-test
@Property(name = "spec.name", value = "OpenTelemetryBuilderCustomizerExampleTest")
@MicronautTest(startApplication = false)
class OpenTelemetryBuilderCustomizerExampleTest {

    private static final String HISTOGRAM_NAME = "http.server.request.duration";

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    InMemoryMetricReader metricReader;

    @Test
    void theCustomizerRegistersTheHistogramView() {
        DoubleHistogram histogram = openTelemetry.getMeter("test").histogramBuilder(HISTOGRAM_NAME).build();

        histogram.record(0.5d);
        histogram.record(6.0d);
        MetricData metric = metricReader.collectAllMetrics().stream()
            .filter(data -> data.getName().equals(HISTOGRAM_NAME))
            .findFirst()
            .orElse(null);

        assertNotNull(metric);
        HistogramPointData point = metric.getHistogramData().getPoints().iterator().next();
        assertEquals(List.of(1.0d, 5.0d, 10.0d), point.getBoundaries());
        assertEquals(List.of(1L, 0L, 1L, 0L), point.getCounts());
    }
}
