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
import io.micronaut.tracing.opentelemetry.DefaultOpenTelemetryFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import jakarta.inject.Singleton;

/**
 * Registers an OpenTelemetry bean for UCP metrics tests.
 */
@Factory
@Replaces(factory = DefaultOpenTelemetryFactory.class)
public class TestDefaultOpenTelemetryFactory {

    /**
     * The OpenTelemetry bean with an in-memory metric reader.
     *
     * @param inMemoryMetricReader the metric reader
     * @return the OpenTelemetry bean
     */
    @Singleton
    @Primary
    OpenTelemetry defaultOpenTelemetry(InMemoryMetricReader inMemoryMetricReader) {
        return OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder()
                .registerMetricReader(inMemoryMetricReader)
                .build())
            .build();
    }

    /**
     * @return the in-memory metric reader.
     */
    @Singleton
    InMemoryMetricReader inMemoryMetricReader() {
        return InMemoryMetricReader.create();
    }
}
