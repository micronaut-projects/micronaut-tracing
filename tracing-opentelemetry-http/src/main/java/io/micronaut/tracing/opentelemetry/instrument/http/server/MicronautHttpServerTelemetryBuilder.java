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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;

import java.util.List;

/**
 * An HTTP server instrumentation builder for Open Telemetry.
 *
 * @author Nemanja Mikic
 */
@SuppressWarnings({"rawtypes", "unused"})
public final class MicronautHttpServerTelemetryBuilder {

    private static final String INSTRUMENTATION_NAME = "io.micronaut.http.server";

    private final OpenTelemetry openTelemetry;
    private final HttpServerAttributesExtractorBuilder<HttpRequest, HttpResponse> httpAttributesExtractorBuilder =
        HttpServerAttributesExtractor.builder(MicronautHttpServerAttributesGetter.INSTANCE);

    public MicronautHttpServerTelemetryBuilder(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * Configures the HTTP request headers to be captured as span attributes.
     *
     * @param requestHeaders HTTP header names.
     * @return the builder
     */
    public MicronautHttpServerTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
        httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
        return this;
    }

    /**
     * Configures the HTTP response headers to be captured as span attributes.
     *
     * @param responseHeaders HTTP header names.
     * @return the builder
     */
    public MicronautHttpServerTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
        httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
        return this;
    }

    public Instrumenter<HttpRequest, HttpResponse> build() {
        MicronautHttpServerAttributesGetter httpAttributesGetter = MicronautHttpServerAttributesGetter.INSTANCE;

        InstrumenterBuilder<HttpRequest, HttpResponse> builder =
            Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter));

        return builder
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractor(
                NetServerAttributesExtractor.create(new MicronautHttpNetServerAttributesGetter()))
            .addRequestMetrics(HttpServerMetrics.get())
            .addContextCustomizer(HttpRouteHolder.get())
            .newServerInstrumenter(HttpRequestGetter.INSTANCE);
    }
}
