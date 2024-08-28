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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;

import jakarta.inject.Named;
import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.Collections;
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
     * @param extractors the list of {@link AttributesExtractor}
     * @return the http client Open Telemetry instrumenter
     */
    @Prototype
    @Requires(beans = OpenTelemetry.class)
    @Named("micronautHttpClientTelemetryInstrumenter")
    Instrumenter<MutableHttpRequest<Object>, HttpResponse<Object>> instrumenter(OpenTelemetry openTelemetry, @Client List<AttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>>> extractors) {

        MicronautHttpClientAttributesGetter httpAttributesGetter = MicronautHttpClientAttributesGetter.INSTANCE;

        InstrumenterBuilder<MutableHttpRequest<Object>, HttpResponse<Object>> builder = Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributesGetter));

        builder.setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addOperationMetrics(HttpClientMetrics.get());
        builder.addAttributesExtractors(extractors);

        return builder.buildClientInstrumenter(HttpRequestSetter.INSTANCE);
    }

    /**
     * Builds the PeerServiceAttributesExtractor.
     * @return the {@link PeerServiceAttributesExtractor}
     */
    @Client
    @Prototype
    AttributesExtractor<MutableHttpRequest<Object>, HttpResponse<Object>> peerServiceAttributesExtractor() {
        return PeerServiceAttributesExtractor.create(MicronautHttpClientAttributesGetter.INSTANCE, PeerServiceResolver.create(Collections.emptyMap()));
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
