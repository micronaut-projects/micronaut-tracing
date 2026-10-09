package io.micronaut.tracing.opentelemetry.instrument.http;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;

/**
 * Lets tests select a metrics exporter with {@code otel.metrics.exporter=micronaut-test}.
 */
public class TestMetricExporterProvider implements ConfigurableMetricExporterProvider {

    public static final String NAME = "micronaut-test";

    @Override
    public MetricExporter createExporter(ConfigProperties config) {
        return InMemoryMetricExporter.create();
    }

    @Override
    public String getName() {
        return NAME;
    }
}
