/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.tracing.opentelemetry.exporter.zipkin;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jakarta.inject.Singleton;
import zipkin2.reporter.Sender;

/**
 * Builds a {@code SpanProcessor} that exports traces to Zipkin.
 */
@Factory
@Requires(missingProperty = "otel.traces.exporter")
@Requires(bean = Sender.class)
public final class OtelHttpClientSenderFactory {

    @Singleton
    public SpanProcessor createExporter(Sender sender) {
        return BatchSpanProcessor.builder(ZipkinSpanExporter.builder().setSender(sender).build()).build();
    }

}

