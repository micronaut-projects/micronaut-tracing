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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.micronaut.tracing.opentelemetry.instrument.http.AbstractOpenTelemetryFilter;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryExclusionsConfiguration;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static io.micronaut.http.filter.ServerFilterPhase.TRACING;
import static io.micronaut.tracing.opentelemetry.instrument.http.server.OpenTelemetryServerFilter.SERVER_PATH;

/**
 * An HTTP server instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Internal
@Filter(SERVER_PATH)
@Requires(beans = Tracer.class)
public final class OpenTelemetryServerFilter extends AbstractOpenTelemetryFilter implements HttpServerFilter {

    private static final String APPLIED = OpenTelemetryServerFilter.class.getName() + "-applied";
    private static final String CONTINUE = OpenTelemetryServerFilter.class.getName() + "-continue";

    private final Instrumenter<HttpRequest<?>, Object> instrumenter;

    /**
     * @param exclusionsConfig The {@link OpenTelemetryExclusionsConfiguration}
     * @param instrumenter     The {@link OpenTelemetryHttpServerConfig}
     */
    public OpenTelemetryServerFilter(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig,
                                     @Named("micronautHttpServerTelemetryInstrumenter") Instrumenter<HttpRequest<?>, Object> instrumenter) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());

        this.instrumenter = instrumenter;
    }

    @Override
    public int getOrder() {
        return TRACING.order();
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        boolean applied = request.getAttribute(APPLIED, Boolean.class).orElse(false);
        boolean continued = request.getAttribute(CONTINUE, Boolean.class).orElse(false);

        if ((applied && !continued) || shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }

        request.setAttribute(APPLIED, true);

        Context parentContext = Context.current();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return chain.proceed(request);
        }

        Context context = instrumenter.start(parentContext, request);

        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(context))
            .propagate()) {

            var propagatedContext = PropagatedContext.get();
            return Mono.from(chain.proceed(request))
                .doOnNext(mutableHttpResponse -> mutableHttpResponse.getAttribute(HttpAttributes.EXCEPTION, Exception.class)
                    .ifPresentOrElse(
                        e -> onError(request, context, mutableHttpResponse, e), () -> {
                            if (mutableHttpResponse.status().getCode() >= 400) {
                                onError(request, context, mutableHttpResponse, null);
                            } else {
                                instrumenter.end(context, request, mutableHttpResponse, null);
                            }
                        }))
                .doOnError(throwable -> onError(request, context, null, throwable))
                .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext));
        }
    }

    private void onError(HttpRequest<?> request, Context context,
                         @Nullable MutableHttpResponse<?> mutableHttpResponse, @Nullable Throwable e) {
        var span = Span.fromContext(context)
            .setStatus(StatusCode.ERROR);
        if (e != null) {
            span.recordException(e);
        }
        instrumenter.end(context, request, mutableHttpResponse, e);
        request.setAttribute(CONTINUE, true);
    }
}
