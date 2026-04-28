/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * Registers an OpenTelemetry bean for test.
 */
@Factory
@Replaces(factory = DefaultOpenTelemetryFactory.class)
public class TestDefaultOpenTelemetryFactory {

    /**
     * The OpenTelemetry bean with default values.
     *
     * @return the OpenTelemetry
     */
    @Singleton
    @Primary
    @Requires(property = "test.open-telemetry.requires-startup-event-publisher", notEquals = "true")
    OpenTelemetry defaultOpenTelemetry(InMemorySpanExporter inMemorySpanExporter) {
        return createOpenTelemetry(inMemorySpanExporter);
    }

    @Singleton
    @Primary
    @Requires(property = "test.open-telemetry.requires-startup-event-publisher", value = "true")
    OpenTelemetry defaultOpenTelemetryRequiringStartupEventPublisher(InMemorySpanExporter inMemorySpanExporter,
                                                                     ApplicationEventPublisher<StartupEvent> ignored) {
        return createOpenTelemetry(inMemorySpanExporter);
    }

    private static OpenTelemetry createOpenTelemetry(InMemorySpanExporter inMemorySpanExporter) {
        return OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(inMemorySpanExporter))
                .build()
            ).build();
    }

    @Singleton
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }
}
