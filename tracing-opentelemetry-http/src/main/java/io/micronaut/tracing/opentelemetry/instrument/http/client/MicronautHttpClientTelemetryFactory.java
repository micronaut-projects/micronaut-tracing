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
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * An HTTP client instrumentation builder for Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
@Factory
public class MicronautHttpClientTelemetryFactory {

    private static final String INSTRUMENTATION_NAME = "io.micronaut.http.client";

    /**
     * Builds the http client Open Telemetry instrumenter.
     * @param openTelemetry the {@link OpenTelemetry}
     * @param clientAttributesExtractor the {@link MicronautClientAttributesExtractor}
     * @return the http client Open Telemetry instrumenter
     */
    @Prototype
    @Requires(beans = OpenTelemetry.class)
    @Named("micronautHttpClientTelemetryInstrumenter")
    Instrumenter<MutableHttpRequest<?>, HttpResponse<?>> instrumenter(OpenTelemetry openTelemetry,
        MicronautClientAttributesExtractor clientAttributesExtractor) {

        MicronautHttpClientAttributesGetter httpAttributesGetter = MicronautHttpClientAttributesGetter.INSTANCE;

        InstrumenterBuilder<MutableHttpRequest<?>, HttpResponse<?>> builder = Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributesGetter));

        builder.setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addOperationMetrics(HttpClientMetrics.get());
        builder.addAttributesExtractors(clientAttributesExtractor.getAttributesExtractors());

        return builder.newClientInstrumenter(HttpRequestSetter.INSTANCE);
    }

    /**
     * Builds the PeerServiceAttributesExtractor.
     * @param micronautHttpNetClientAttributesGetter the {@link MicronautHttpNetClientAttributesGetter}
     * @return the {@link PeerServiceAttributesExtractor}
     */
    @Prototype
    PeerServiceAttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>> peerServiceAttributesExtractor(MicronautHttpNetClientAttributesGetter micronautHttpNetClientAttributesGetter) {
        return PeerServiceAttributesExtractor.create(micronautHttpNetClientAttributesGetter);
    }

    /**
     * Builds the MicronautHttpNetClientAttributesGetter.
     * @return the {@link MicronautHttpNetClientAttributesGetter}
     */
    @Prototype
    MicronautHttpNetClientAttributesGetter micronautHttpNetClientAttributesGetter() {
       return new MicronautHttpNetClientAttributesGetter();
    }

    /**
     * Builds the PeerServiceAttributesExtractor.
     * @param micronautHttpNetClientAttributesGetter the {@link MicronautHttpNetClientAttributesGetter}
     * @return the {@link PeerServiceAttributesExtractor}
     */
    @Prototype
    NetClientAttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>>  micronautHttpNetClientAttributesGetter(MicronautHttpNetClientAttributesGetter micronautHttpNetClientAttributesGetter) {
        return NetClientAttributesExtractor.create(micronautHttpNetClientAttributesGetter);
    }

    /**
     * Builds the HttpClientAttributesExtractor.
     * @param openTelemetryHttpClientConfig the {@link OpenTelemetryHttpClientConfig}
     * @return the {@link HttpClientAttributesExtractor}
     */
    @Prototype
    HttpClientAttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>> mutableHttpRequestHttpResponseHttpClientAttributesExtractorBuilder(@Nullable OpenTelemetryHttpClientConfig openTelemetryHttpClientConfig) {
        HttpClientAttributesExtractorBuilder<MutableHttpRequest<?>, HttpResponse<?>> httpAttributesExtractorBuilder =
            HttpClientAttributesExtractor.builder(MicronautHttpClientAttributesGetter.INSTANCE);

        if (openTelemetryHttpClientConfig != null) {
            httpAttributesExtractorBuilder.setCapturedRequestHeaders(openTelemetryHttpClientConfig.getRequestHeaders());
            httpAttributesExtractorBuilder.setCapturedResponseHeaders(openTelemetryHttpClientConfig.getResponseHeaders());
        }
        return httpAttributesExtractorBuilder.build();
    }

    /**
     * Builds the MicronautClientAttributesExtractor.
     * @param httpRequestHttpResponseHttpClientAttributesExtractor the {@link HttpClientAttributesExtractor}
     * @param netClientAttributesExtractor the {@link NetClientAttributesExtractor}
     * @param peerServiceAttributesExtractor the {@link PeerServiceAttributesExtractor}
     * @return the {@link HttpClientAttributesExtractor}
     */
    @Prototype
    MicronautClientAttributesExtractor clientAttributesExtractor(
        HttpClientAttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>> httpRequestHttpResponseHttpClientAttributesExtractor,
        NetClientAttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>> netClientAttributesExtractor,
        PeerServiceAttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>> peerServiceAttributesExtractor
        ) {
        return new MicronautClientAttributesExtractor(new ArrayList<>(Arrays.asList(httpRequestHttpResponseHttpClientAttributesExtractor, netClientAttributesExtractor, peerServiceAttributesExtractor)));
    }
}
