package io.micronaut.tracing.util;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.tracing.opentelemetry.DefaultOpenTelemetryFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.inject.Singleton;

/**
 * Replaces the OpenTelemetry bean with one that depends on the startup event publisher, to reproduce
 * eager-startup bean cycles. The in-memory exporter comes from micronaut-tracing-opentelemetry-test.
 */
@Factory
@Requires(property = "test.open-telemetry.requires-startup-event-publisher", value = "true")
public class StartupEventDependentOpenTelemetryFactory {

    @Singleton
    @Primary
    @Replaces(factory = DefaultOpenTelemetryFactory.class, bean = OpenTelemetry.class)
    OpenTelemetry openTelemetryRequiringStartupEventPublisher(InMemorySpanExporter inMemorySpanExporter,
                                                              ApplicationEventPublisher<StartupEvent> ignored) {
        return OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(inMemorySpanExporter))
                .build()
            ).build();
    }
}
