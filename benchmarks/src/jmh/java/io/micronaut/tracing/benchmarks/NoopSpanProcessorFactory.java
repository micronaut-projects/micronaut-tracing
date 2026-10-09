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
package io.micronaut.tracing.benchmarks;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Registers a {@link BatchSpanProcessor} that exports to a no-op exporter, so sampled spans go through the
 * whole SDK pipeline (end, queue, export) without any I/O.
 */
@Factory
@Requires(property = NoopSpanProcessorFactory.PROPERTY, value = StringUtils.TRUE)
public class NoopSpanProcessorFactory {

    public static final String PROPERTY = "benchmarks.noop-span-exporter";

    /**
     * @return the span processor
     */
    @Singleton
    @Bean(preDestroy = "close")
    SpanProcessor noopBatchSpanProcessor() {
        // an empty composite is the SDK's no-op exporter
        return BatchSpanProcessor.builder(SpanExporter.composite(List.of())).build();
    }
}
