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
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import io.opentelemetry.sdk.common.internal.OtelVersion;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

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
     * Server Qualifier represents AttributesExtractor that should be used in http server instrumenter.
     */
    @Qualifier
    @Documented
    @Retention(RUNTIME)
    public @interface Server { }

    /**
     * Builds the http server Open Telemetry instrumenter.
     * @param openTelemetry the {@link OpenTelemetry}
     * @param spanNameExtractor the {@link SpanNameExtractor}
     * @param spanStatusExtractor the {@link SpanStatusExtractor}, can be {@code null}
     * @param errorCauseExtractor the {@link ErrorCauseExtractor}, can be {@code null}
     * @param attributesExtractors the list of {@link AttributesExtractor}
     * @param contextCustomizers the list of {@link ContextCustomizer}
     * @param operationMetrics the list of {@link OperationMetrics}
     * @param operationListeners the list of {@link OperationListener}
     * @param spanLinksExtractors the list of {@link SpanLinksExtractor}
     * @return the http server Open Telemetry instrumenter
     */
    @Prototype
    @Requires(beans = OpenTelemetry.class)
    @SuppressWarnings("DuplicatedCode")
    @Named("micronautHttpServerTelemetryInstrumenter")
    public Instrumenter<HttpRequest<Object>, HttpResponse<Object>> instrumenter(
        OpenTelemetry openTelemetry,
        @Server SpanNameExtractor<HttpRequest<?>> spanNameExtractor,
        @Server @Nullable SpanStatusExtractor<HttpRequest<?>, HttpResponse<?>> spanStatusExtractor,
        @Server @Nullable ErrorCauseExtractor errorCauseExtractor,
        @Server List<AttributesExtractor<HttpRequest<?>, HttpResponse<?>>> attributesExtractors,
        @Server List<ContextCustomizer<HttpRequest<?>>> contextCustomizers,
        @Server List<OperationMetrics> operationMetrics,
        @Server List<OperationListener> operationListeners,
        @Server List<SpanLinksExtractor<HttpRequest<Object>>> spanLinksExtractors) {

        InstrumenterBuilder<HttpRequest<Object>, HttpResponse<Object>> builder =
            Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor);

        builder.setInstrumentationVersion(OtelVersion.VERSION);
        if (spanStatusExtractor != null) {
            builder.setSpanStatusExtractor(spanStatusExtractor);
        }
        if (errorCauseExtractor != null) {
            builder.setErrorCauseExtractor(errorCauseExtractor);
        }
        builder.addAttributesExtractors(attributesExtractors);
        contextCustomizers.forEach(builder::addContextCustomizer);
        operationListeners.forEach(builder::addOperationListener);
        operationMetrics.forEach(builder::addOperationMetrics);
        spanLinksExtractors.forEach(builder::addSpanLinksExtractor);

        return builder.buildServerInstrumenter(HttpRequestGetter.INSTANCE);
    }

    /**
     * Builds the default {@link SpanNameExtractor}.
     * @return the {@link HttpSpanNameExtractor}
     */
    @Server
    @Singleton
    SpanNameExtractor<HttpRequest<Object>> defaultSpanNameExtractor() {
        return HttpSpanNameExtractor.create(MicronautHttpServerAttributesGetter.INSTANCE);
    }

    /**
     * Builds the default {@link SpanStatusExtractor}.
     * @return the {@link HttpSpanStatusExtractor}
     */
    @Server
    @Singleton
    SpanStatusExtractor<HttpRequest<Object>, HttpResponse<Object>> defaultSpanStatusExtractor() {
        return HttpSpanStatusExtractor.create(MicronautHttpServerAttributesGetter.INSTANCE);
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

    /**
     * Returns an {@link OperationMetrics} instance which can be used to enable recording of {@link
     * HttpServerMetrics}.
     * @return the {@link OperationMetrics} instance
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Server
    @Singleton
    OperationMetrics httpServerMetrics() {
        return HttpServerMetrics.get();
    }

    /**
     * Builds a {@link ContextCustomizer} that initializes the {@code http.route} attribute value.
     * @return the {@link ContextCustomizer}
     */
    @Server
    @Singleton
    ContextCustomizer<HttpRequest<Object>> httpServerRouteCustomizer() {
        return HttpServerRoute.create(MicronautHttpServerAttributesGetter.INSTANCE);
    }
}
