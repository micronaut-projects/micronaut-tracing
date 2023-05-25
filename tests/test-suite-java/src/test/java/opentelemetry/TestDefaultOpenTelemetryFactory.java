package opentelemetry;

import io.micronaut.context.annotation.Factory;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.inject.Singleton;

@Factory
public class TestDefaultOpenTelemetryFactory {

    @Singleton
    SpanProcessor spanProcessor(InMemorySpanExporter spanExporter) {
        return SimpleSpanProcessor.create(spanExporter);
    }

    @Singleton
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

}
