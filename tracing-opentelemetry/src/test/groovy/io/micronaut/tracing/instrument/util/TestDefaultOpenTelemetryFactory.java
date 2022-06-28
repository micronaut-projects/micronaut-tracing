/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tracing.instrument.util;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "AnnotationMappingSpec")
@Factory
public class TestDefaultOpenTelemetryFactory {

    @Singleton
    SpanProcessor spanProcessor(InMemorySpanExporter spanExporter) {
        return SimpleSpanProcessor.create(spanExporter);
    }

    @Singleton
    IdGenerator idGenerator() {
        return IdGenerator.random();
    }

    @Singleton
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }
}
