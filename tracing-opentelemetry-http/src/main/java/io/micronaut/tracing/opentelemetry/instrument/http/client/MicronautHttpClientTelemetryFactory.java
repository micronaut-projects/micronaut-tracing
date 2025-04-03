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
package io.micronaut.tracing.opentelemetry.instrument.http.client;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientServicePeerAttributesExtractor;
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
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
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
 * An HTTP client instrumentation builder for Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Factory
public class MicronautHttpClientTelemetryFactory {

    /**
     * Client Qualifier represents AttributesExtractor that should be used in http client instrumenter.
     */
    @Qualifier
    @Documented
    @Retention(RUNTIME)
    public @interface Client { }

    private static final String INSTRUMENTATION_NAME = "io.micronaut.http.client";

    /**
     * Builds the http client Open Telemetry instrumenter.
     * @param openTelemetry the {@link OpenTelemetry}
     * @param spanNameExtractor the {@link SpanNameExtractor}
     * @param spanStatusExtractor the {@link SpanStatusExtractor}, can be {@code null}
     * @param errorCauseExtractor the {@link ErrorCauseExtractor}, can be {@code null}
     * @param attributesExtractors the list of {@link AttributesExtractor}
     * @param contextCustomizers the list of {@link ContextCustomizer}
     * @param operationMetrics the list of {@link OperationMetrics}
     * @param operationListeners the list of {@link OperationListener}
     * @param spanLinksExtractors the list of {@link SpanLinksExtractor}
     * @return the http client Open Telemetry instrumenter
     */
    @Prototype
    @Requires(beans = OpenTelemetry.class)
    @SuppressWarnings("DuplicatedCode")
    @Named("micronautHttpClientTelemetryInstrumenter")
    Instrumenter<MutableHttpRequest<Object>, HttpResponse<Object>> instrumenter(
        OpenTelemetry openTelemetry,
        @Client SpanNameExtractor<MutableHttpRequest<?>> spanNameExtractor,
        @Client @Nullable SpanStatusExtractor<MutableHttpRequest<?>, HttpResponse<?>> spanStatusExtractor,
        @Client @Nullable ErrorCauseExtractor errorCauseExtractor,
        @Client List<AttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>>> attributesExtractors,
        @Client List<ContextCustomizer<MutableHttpRequest<?>>> contextCustomizers,
        @Client List<OperationMetrics> operationMetrics,
        @Client List<OperationListener> operationListeners,
        @Client List<SpanLinksExtractor<MutableHttpRequest<Object>>> spanLinksExtractors) {

        InstrumenterBuilder<MutableHttpRequest<Object>, HttpResponse<Object>> builder =
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

        return builder.buildClientInstrumenter(HttpRequestSetter.INSTANCE);
    }

    /**
     * Builds the default {@link SpanNameExtractor}.
     * @return the {@link HttpSpanNameExtractor}
     */
    @Client
    @Singleton
    SpanNameExtractor<MutableHttpRequest<Object>> defaultSpanNameExtractor() {
        return HttpSpanNameExtractor.create(MicronautHttpClientAttributesGetter.INSTANCE);
    }

    /**
     * Builds the default {@link SpanStatusExtractor}.
     * @return the {@link HttpSpanStatusExtractor}
     */
    @Client
    @Singleton
    SpanStatusExtractor<MutableHttpRequest<Object>, HttpResponse<Object>> defaultSpanStatusExtractor() {
        return HttpSpanStatusExtractor.create(MicronautHttpClientAttributesGetter.INSTANCE);
    }

    /**
     * Returns an {@link OperationMetrics} instance which can be used to enable recording of {@link
     * HttpClientMetrics}.
     * @return the {@link OperationMetrics} instance
     */
    @Client
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Singleton
    OperationMetrics httpClientMetrics() {
        return HttpClientMetrics.get();
    }

    /**
     * Builds the HttpClientServicePeerAttributesExtractor.
     * @param openTelemetry the {@link OpenTelemetry}
     * @return the {@link HttpClientServicePeerAttributesExtractor}
     */
    @Client
    @Prototype
    AttributesExtractor<MutableHttpRequest<Object>, HttpResponse<Object>> peerServiceAttributesExtractor(OpenTelemetry openTelemetry) {
        return HttpClientServicePeerAttributesExtractor.create(MicronautHttpClientAttributesGetter.INSTANCE, openTelemetry);
    }

    /**
     * Builds the HttpClientAttributesExtractor.
     * @param openTelemetryHttpClientConfig the {@link OpenTelemetryHttpClientConfig}
     * @return the {@link HttpClientAttributesExtractor}
     */
    @Client
    @Prototype
    AttributesExtractor<MutableHttpRequest<Object>, HttpResponse<Object>> mutableHttpRequestHttpResponseHttpClientAttributesExtractorBuilder(@Nullable OpenTelemetryHttpClientConfig openTelemetryHttpClientConfig) {
        HttpClientAttributesExtractorBuilder<MutableHttpRequest<Object>, HttpResponse<Object>> httpAttributesExtractorBuilder =
            HttpClientAttributesExtractor.builder(MicronautHttpClientAttributesGetter.INSTANCE);

        if (openTelemetryHttpClientConfig != null) {
            httpAttributesExtractorBuilder.setCapturedRequestHeaders(openTelemetryHttpClientConfig.getRequestHeaders());
            httpAttributesExtractorBuilder.setCapturedResponseHeaders(openTelemetryHttpClientConfig.getResponseHeaders());
        }
        return httpAttributesExtractorBuilder.build();
    }
}
