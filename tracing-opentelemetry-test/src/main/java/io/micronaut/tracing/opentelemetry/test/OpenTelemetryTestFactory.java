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
package io.micronaut.tracing.opentelemetry.test;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Registers an {@link InMemorySpanExporter} and an {@link InMemoryMetricReader} with the OpenTelemetry SDK
 * built by {@code DefaultOpenTelemetryFactory}, through {@link OpenTelemetryBuilderCustomizer} beans.
 *
 * <p>Spans are exported with a {@link SimpleSpanProcessor}, so a span is visible to
 * {@link InMemorySpanExporter#getFinishedSpanItems()} as soon as it ends.</p>
 *
 * <p>The beans are active whenever this module is on the classpath and {@value #ENABLED} is not
 * {@code false}. They deliberately do not require the {@code test} environment, because many tests start an
 * {@code ApplicationContext} without it; add this module to the test classpath only.</p>
 *
 * @since 8.4.0
 */
@Factory
public final class OpenTelemetryTestFactory {

    /**
     * The property prefix.
     */
    public static final String PREFIX = "tracing.opentelemetry.test";

    /**
     * Enables the test support. Default {@code true}.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    /**
     * Registers the {@link InMemoryMetricReader} with the meter provider. Default {@code true}.
     */
    public static final String METRICS_ENABLED = PREFIX + ".metrics.enabled";

    /**
     * Resets the captured spans before each {@code @MicronautTest} test method. Default {@code true}.
     */
    public static final String RESET_BEFORE_EACH = PREFIX + ".reset-before-each";

    /**
     * @return the in-memory span exporter
     */
    @Singleton
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

    /**
     * @return the in-memory metric reader
     */
    @Singleton
    InMemoryMetricReader inMemoryMetricReader() {
        return InMemoryMetricReader.create();
    }

    /**
     * Adds a {@link SimpleSpanProcessor} exporting to the in-memory span exporter.
     *
     * @param exporter the in-memory span exporter
     * @return the customizer
     */
    @Singleton
    @Named("inMemorySpanExporter")
    OpenTelemetryBuilderCustomizer inMemorySpanExporterCustomizer(InMemorySpanExporter exporter) {
        return builder -> builder.addTracerProviderCustomizer((tracerProviderBuilder, ignored) ->
            tracerProviderBuilder.addSpanProcessor(SimpleSpanProcessor.create(exporter)));
    }

    /**
     * Registers the in-memory metric reader with the meter provider.
     *
     * @param metricReader the in-memory metric reader
     * @return the customizer
     */
    @Singleton
    @Named("inMemoryMetricReader")
    @Requires(property = METRICS_ENABLED, notEquals = StringUtils.FALSE)
    OpenTelemetryBuilderCustomizer inMemoryMetricReaderCustomizer(InMemoryMetricReader metricReader) {
        return builder -> builder.addMeterProviderCustomizer((meterProviderBuilder, ignored) ->
            meterProviderBuilder.registerMetricReader(metricReader));
    }
}
