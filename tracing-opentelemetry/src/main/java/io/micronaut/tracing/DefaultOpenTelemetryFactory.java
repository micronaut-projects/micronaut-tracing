package io.micronaut.tracing;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.inject.Singleton;

@Factory
public class DefaultOpenTelemetryFactory {

    @Singleton
    @Primary
    OpenTelemetry defaultOpenTelemetry() {
        return GlobalOpenTelemetry.get();
    }
}
