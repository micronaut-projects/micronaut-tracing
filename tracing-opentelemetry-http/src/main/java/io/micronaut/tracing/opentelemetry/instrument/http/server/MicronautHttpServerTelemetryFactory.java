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
package io.micronaut.tracing.opentelemetry.instrument.http.server;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An HTTP server instrumentation builder for Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Factory
public final class MicronautHttpServerTelemetryFactory {

    private static final String INSTRUMENTATION_NAME = "io.micronaut.http.server";

    /**
     * Server Qualifier represents AttributesExtractor that should be used in http srver instrumenter.
     */
    @Qualifier
    @Documented
    @Retention(RUNTIME)
    public @interface Server { }

    /**
     * Builds the http server Open Telemetry instrumenter.
     * @param openTelemetry the {@link OpenTelemetry}
     * @param extractors the list of {@link AttributesExtractor}
     * @return the http server Open Telemetry instrumenter
     */
    @Prototype
    @Requires(beans = OpenTelemetry.class)
    @Named("micronautHttpServerTelemetryInstrumenter")
    public Instrumenter<HttpRequest<Object>, HttpResponse<Object>> instrumenter(OpenTelemetry openTelemetry, @Server List<AttributesExtractor<HttpRequest<?>, HttpResponse<?>>> extractors) {
        MicronautHttpServerAttributesGetter httpAttributesGetter = MicronautHttpServerAttributesGetter.INSTANCE;

        InstrumenterBuilder<HttpRequest<Object>, HttpResponse<Object>> builder =
            Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter));

        builder.addAttributesExtractors(extractors);

        return builder
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(HttpServerRoute.create(MicronautHttpServerAttributesGetter.INSTANCE))
            .buildServerInstrumenter(HttpRequestGetter.INSTANCE);
    }

    /**
     * Builds the HttpServerAttributesExtractor.
     * @param openTelemetryHttpServerConfig the {@link OpenTelemetryHttpServerConfig}
     * @return the {@link HttpServerAttributesExtractor}
     */
    @Prototype
    @Server
    AttributesExtractor<HttpRequest<Object>, HttpResponse<Object>> httpServerAttributesExtractor(@Nullable OpenTelemetryHttpServerConfig openTelemetryHttpServerConfig) {
        HttpServerAttributesExtractorBuilder<HttpRequest<Object>, HttpResponse<Object>> httpAttributesExtractorBuilder =
            HttpServerAttributesExtractor.builder(MicronautHttpServerAttributesGetter.INSTANCE);

        if (openTelemetryHttpServerConfig != null) {
            httpAttributesExtractorBuilder.setCapturedRequestHeaders(openTelemetryHttpServerConfig.getRequestHeaders());
            httpAttributesExtractorBuilder.setCapturedResponseHeaders(openTelemetryHttpServerConfig.getResponseHeaders());
        }
        return httpAttributesExtractorBuilder.build();
    }
}
