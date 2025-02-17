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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseProvider;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.micronaut.tracing.opentelemetry.instrument.http.AbstractOpenTelemetryFilter;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryExclusionsConfiguration;
import io.micronaut.tracing.opentelemetry.interceptor.AbstractOpenTelemetryTraceInterceptor;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static io.micronaut.http.HttpAttributes.INVOCATION_CONTEXT;
import static io.micronaut.tracing.opentelemetry.instrument.http.client.OpenTelemetryClientFilter.CLIENT_PATH;

/**
 * An HTTP client instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Internal
@Filter(CLIENT_PATH)
public final class OpenTelemetryClientFilter extends AbstractOpenTelemetryFilter implements HttpClientFilter {

    private final Instrumenter<MutableHttpRequest<?>, Object> instrumenter;

    /**
     * Initialize the open tracing client filter with tracer and exclusion configuration.
     *
     * @param exclusionsConfig The {@link OpenTelemetryExclusionsConfiguration}
     * @param instrumenter The {@link OpenTelemetryHttpClientConfig}
     */
    public OpenTelemetryClientFilter(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig,
                                     @Named("micronautHttpClientTelemetryInstrumenter") Instrumenter<MutableHttpRequest<?>, Object> instrumenter) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
        this.instrumenter = instrumenter;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                         ClientFilterChain chain) {

        if (shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }

        Context parentContext = Context.current();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return chain.proceed(request);
        }

        Context context = instrumenter.start(parentContext, request);

        try (Scope ignored = context.makeCurrent()) {
            handleContinueSpan(request);

            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
                .plus(new OpenTelemetryPropagationContext(context))
                .propagate()) {

                return Mono.from(chain.proceed(request))
                    .doOnNext(mutableHttpResponse -> instrumenter.end(context, request, mutableHttpResponse, null))
                    .doOnError(throwable -> {
                        Span span = Span.fromContext(context);
                        span.recordException(throwable);
                        span.setStatus(StatusCode.ERROR);
                        HttpResponse<?> response = findResponseInThrowable(throwable).orElse(null);
                        instrumenter.end(context, request, response, throwable);
                    });

            }
        }
    }

    private Optional<HttpResponse<?>> findResponseInThrowable(@Nullable Throwable throwable) {
        if (throwable instanceof HttpResponseProvider httpResponseProvider) {
            return Optional.ofNullable(httpResponseProvider.getResponse());
        }
        return Optional.empty();
    }

    private void handleContinueSpan(MutableHttpRequest<?> request) {
        Object invocationContext = request.getAttribute(INVOCATION_CONTEXT).orElse(null);
        if (invocationContext instanceof MethodInvocationContext<?, ?> context) {
            if (context.hasAnnotation(ContinueSpan.class)) {
                AbstractOpenTelemetryTraceInterceptor.tagArguments(context);
            }
        }
    }
}
