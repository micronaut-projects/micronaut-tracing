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

import io.micronaut.http.HttpRequest;
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

import java.util.List;

/**
 * An HTTP client instrumentation builder for Open Telemetry.
 *
 * @author Nemanja Mikic
 */
@SuppressWarnings({"rawtypes", "unused"})
public final class MicronautHttpClientTelemetryBuilder {

    private static final String INSTRUMENTATION_NAME = "io.micronaut.http.client";

    private final OpenTelemetry openTelemetry;
    private final HttpClientAttributesExtractorBuilder<HttpRequest, HttpResponse> httpAttributesExtractorBuilder =
        HttpClientAttributesExtractor.builder(MicronautHttpClientAttributesGetter.INSTANCE);

    public MicronautHttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * Configures the HTTP request headers that will be captured as span attributes.
     *
     * @param requestHeaders A list of HTTP header names.
     * @return MicronautHttpClientTelemetryBuilder builder
     */
    public MicronautHttpClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
        httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
        return this;
    }

    /**
     * Configures the HTTP response headers that will be captured as span attributes.
     *
     * @param responseHeaders A list of HTTP header names.
     * @return MicronautHttpClientTelemetryBuilder builder
     */
    public MicronautHttpClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
        httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
        return this;
    }

    public Instrumenter<MutableHttpRequest, HttpResponse> build() {
        MicronautHttpClientAttributesGetter httpAttributesGetter = MicronautHttpClientAttributesGetter.INSTANCE;
        MicronautHttpNetClientAttributesGetter netAttributesGetter = new MicronautHttpNetClientAttributesGetter();

        InstrumenterBuilder<MutableHttpRequest, HttpResponse> builder =
            Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributesGetter));

        return builder.setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpRequestSetter.INSTANCE);
    }
}
