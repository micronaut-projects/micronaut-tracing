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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.opentelemetry.instrument.http.AbstractOpenTelemetryFilter;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryExclusionsConfiguration;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryObserver;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryPublisherUtils;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;
import org.reactivestreams.Publisher;

import static io.micronaut.tracing.opentelemetry.instrument.http.server.OpenTelemetryServerFilter.SERVER_PATH;

/**
 * An HTTP server instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
@Filter(SERVER_PATH)
@Requires(beans = Tracer.class)
public class OpenTelemetryServerFilter extends AbstractOpenTelemetryFilter implements HttpServerFilter {

    private static final String APPLIED = OpenTelemetryServerFilter.class.getName() + "-applied";
    private static final String CONTINUE = OpenTelemetryServerFilter.class.getName() + "-continue";

    private final Instrumenter<HttpRequest<?>, Object> instrumenter;

    /**
     * @param exclusionsConfig The {@link OpenTelemetryExclusionsConfiguration}
     * @param instrumenter The {@link OpenTelemetryHttpServerConfig}
     */
    public OpenTelemetryServerFilter(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig,
                                     @Named("micronautHttpServerTelemetryInstrumenter") Instrumenter<HttpRequest<?>, Object> instrumenter) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());

        this.instrumenter = instrumenter;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        boolean applied = request.getAttribute(APPLIED, Boolean.class).orElse(false);
        boolean continued = request.getAttribute(CONTINUE, Boolean.class).orElse(false);

        Publisher<MutableHttpResponse<?>> requestPublisher = chain.proceed(request);

        if ((applied && !continued) || shouldExclude(request.getPath())) {
            return requestPublisher;
        }

        request.setAttribute(APPLIED, true);

        Context parentContext = Context.current();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return requestPublisher;
        }

        Context newContext = instrumenter.start(parentContext, request);

        return OpenTelemetryPublisherUtils.createOpenTelemetryPublisher(requestPublisher, instrumenter, newContext, request, new OpenTelemetryObserver<MutableHttpResponse<?>>() {
            @Override
            public void doOnError(@NonNull Throwable throwable, @NonNull Context openTelemetryContext) {
                request.setAttribute(CONTINUE, true);
                OpenTelemetryPublisherUtils.logError(openTelemetryContext, throwable);
            }
        });
    }
}
