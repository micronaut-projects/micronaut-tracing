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
import io.micronaut.tracing.opentelemetry.instrument.http.client.OpenTelemetryHttpClientConfig;
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
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * An HTTP server instrumentation builder for Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
@Factory
public final class MicronautHttpServerTelemetryFactory {

    private static final String INSTRUMENTATION_NAME = "io.micronaut.http.server";

    /**
     * Builds the http server Open Telemetry instrumenter.
     * @param openTelemetry the {@link OpenTelemetry}
     * @param micronautServerAttributesExtractor the {@link MicronautServerAttributesExtractor}
     * @return the http server Open Telemetry instrumenter
     */
    @Prototype
    @Requires(beans = OpenTelemetry.class)
    @Named("micronautHttpServerTelemetryInstrumenter")
    public Instrumenter<HttpRequest<?>, HttpResponse<?>> instrumenter(OpenTelemetry openTelemetry, MicronautServerAttributesExtractor micronautServerAttributesExtractor) {
        MicronautHttpServerAttributesGetter httpAttributesGetter = MicronautHttpServerAttributesGetter.INSTANCE;

        InstrumenterBuilder<HttpRequest<?>, HttpResponse<?>> builder =
            Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter));

        builder.addAttributesExtractors(micronautServerAttributesExtractor.getAttributesExtractors());

        return builder
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(HttpRouteHolder.get())
            .newServerInstrumenter(HttpRequestGetter.INSTANCE);
    }

    /**
     * Builds the HttpServerAttributesExtractor.
     * @param openTelemetryHttpServerConfig the {@link OpenTelemetryHttpClientConfig}
     * @return the {@link HttpServerAttributesExtractor}
     */
    @Prototype
    HttpServerAttributesExtractor<HttpRequest<?>, HttpResponse<?>> httpServerAttributesExtractor(@Nullable OpenTelemetryHttpServerConfig openTelemetryHttpServerConfig) {
        HttpServerAttributesExtractorBuilder<HttpRequest<?>, HttpResponse<?>> httpAttributesExtractorBuilder =
            HttpServerAttributesExtractor.builder(MicronautHttpServerAttributesGetter.INSTANCE);

        if (openTelemetryHttpServerConfig != null) {
            httpAttributesExtractorBuilder.setCapturedRequestHeaders(openTelemetryHttpServerConfig.getRequestHeaders());
            httpAttributesExtractorBuilder.setCapturedResponseHeaders(openTelemetryHttpServerConfig.getResponseHeaders());
        }
        return httpAttributesExtractorBuilder.build();
    }

    /**
     * Builds the NetServerAttributesExtractor.
     * @return the {@link NetServerAttributesExtractor}
     */
    @Prototype
    NetServerAttributesExtractor<HttpRequest<?>, HttpResponse<?>> netServerAttributesExtractor() {
        return NetServerAttributesExtractor.create(new MicronautHttpNetServerAttributesGetter());
    }

    /**
     * Builds the MicronautServerAttributesExtractor.
     * @param httpServerAttributesExtractor the {@link HttpServerAttributesExtractor}
     * @param netServerAttributesExtractor the {@link NetServerAttributesExtractor}
     * @return the {@link MicronautServerAttributesExtractor}
     */
    @Prototype
    MicronautServerAttributesExtractor serverAttributesExtractor(HttpServerAttributesExtractor<HttpRequest<?>, HttpResponse<?>> httpServerAttributesExtractor,
                                                                 NetServerAttributesExtractor<HttpRequest<?>, HttpResponse<?>> netServerAttributesExtractor) {
        return new MicronautServerAttributesExtractor(new ArrayList<>(Arrays.asList(httpServerAttributesExtractor, netServerAttributesExtractor)));
    }
}
