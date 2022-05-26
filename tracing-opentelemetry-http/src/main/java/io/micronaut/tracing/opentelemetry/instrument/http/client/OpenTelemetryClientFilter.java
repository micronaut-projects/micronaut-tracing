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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.opentelemetry.instrument.http.AbstractOpenTelemetryFilter;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryExclusionsConfiguration;
import io.micronaut.tracing.opentelemetry.instrument.util.TracingPublisherUtils;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;
import org.reactivestreams.Publisher;

import static io.micronaut.tracing.opentelemetry.instrument.http.client.OpenTelemetryClientFilter.CLIENT_PATH;

/**
 * An HTTP client instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
@Filter(CLIENT_PATH)
public class OpenTelemetryClientFilter extends AbstractOpenTelemetryFilter implements HttpClientFilter {

    private final Instrumenter<MutableHttpRequest<?>, HttpResponse<?>> instrumenter;

    /**
     * Initialize the open tracing client filter with tracer and exclusion configuration.
     *
     * @param exclusionsConfig The {@link OpenTelemetryExclusionsConfiguration}
     * @param instrumenter The {@link OpenTelemetryHttpClientConfig}
     */
    public OpenTelemetryClientFilter(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig, @Named("micronautHttpClientTelemetryInstrumenter") Instrumenter<MutableHttpRequest<?>, HttpResponse<?>> instrumenter) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
        this.instrumenter = instrumenter;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                         ClientFilterChain chain) {

        Publisher<? extends HttpResponse<?>> requestPublisher = chain.proceed(request);

        if (shouldExclude(request.getPath())) {
            return requestPublisher;
        }

        Context parentContext = Context.current();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return requestPublisher;
        }

        return TracingPublisherUtils.createTracingPublisher(requestPublisher, instrumenter, request);

    }
}
