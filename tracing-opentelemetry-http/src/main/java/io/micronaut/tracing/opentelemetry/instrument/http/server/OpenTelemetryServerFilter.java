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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryExclusionsConfiguration;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static io.micronaut.http.filter.ServerFilterPhase.TRACING;
import static io.micronaut.tracing.opentelemetry.instrument.http.AbstractOpenTelemetryFilter.SERVER_PATH;

/**
 * An HTTP server instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Internal
@Filter(SERVER_PATH)
@Requires(beans = Tracer.class)
public final class OpenTelemetryServerFilter implements HttpServerFilter {

    static final String CONTEXT = OpenTelemetryServerFilter.class.getName() + "-context";
    static final String CONTINUE = OpenTelemetryServerFilter.class.getName() + "-continue";

    private static final String APPLIED = OpenTelemetryServerFilter.class.getName() + "-applied";

    @Nullable
    private final Predicate<String> pathExclusionTest;
    private final Instrumenter<HttpRequest<?>, Object> instrumenter;

    /**
     * @param exclusionsConfig The {@link OpenTelemetryExclusionsConfiguration}
     * @param instrumenter     The {@link OpenTelemetryHttpServerConfig}
     */
    public OpenTelemetryServerFilter(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig,
                                     @Named("micronautHttpServerTelemetryInstrumenter") Instrumenter<HttpRequest<?>, Object> instrumenter) {
        this.pathExclusionTest = exclusionsConfig == null ? null : exclusionsConfig.exclusionTest();
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

        Context parentContext = parentContext();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return chain.proceed(request);
        }

        Context context = instrumenter.start(parentContext, request);
        request.setAttribute(CONTEXT, context);
        // a new span has been started (e.g. the filter re-runs after an error), it has not been finished yet
        request.removeAttribute(OpenTelemetryServerResponseFilter.FINISHED, Boolean.class);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(context));
        return propagatedContext.propagate(() -> {
            PropagatedContext currentContext = PropagatedContext.get();
            AtomicBoolean signalled = new AtomicBoolean();
            return Mono.from(chain.proceed(request))
                .doOnNext(response -> signalled.set(true))
                .doOnError(throwable -> {
                    signalled.set(true);
                    onError(request, context, throwable);
                })
                .doOnCancel(() -> {
                    if (!signalled.get()) {
                        onCancel(request, context);
                    }
                })
                .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, currentContext));
        });
    }

    /**
     * Ends the span when the request is cancelled (e.g. the client disconnected) before a response was
     * produced, because the response filters that normally finish the span will not run. As for the
     * HTTP client filter, the span is ended without a response and the status is left unset: the server
     * did not fail and OpenTelemetry semantic conventions only mark server spans as errors for 5xx
     * responses or errors.
     */
    private void onCancel(HttpRequest<?> request, Context context) {
        if (request.getAttribute(OpenTelemetryServerResponseFilter.FINISHED, Boolean.class).orElse(false)) {
            return;
        }
        request.setAttribute(OpenTelemetryServerResponseFilter.FINISHED, true);
        instrumenter.end(context, request, null, null);
    }

    /**
     * Resolves the parent context of the server span. Only a context propagated by Micronaut is trusted.
     * The thread-local {@link Context#current()} is deliberately ignored: a server request starts on an
     * event-loop thread, and a scope opened with {@code Span.makeCurrent()} by a previous request and closed
     * on another thread (e.g. after a Reactor boundary) leaves that request's context, including its server
     * span, current on the event-loop thread. Using it would suppress or wrongly parent the next server span
     * (see issue #475). A remote parent is still extracted from the request headers by the instrumenter.
     *
     * @return the parent context
     */
    private static Context parentContext() {
        return PropagatedContext.getOrEmpty()
            .find(OpenTelemetryPropagationContext.class)
            .map(OpenTelemetryPropagationContext::context)
            .orElseGet(Context::root);
    }

    private void onError(HttpRequest<?> request, Context context, Throwable e) {
        Span.fromContext(context)
            .setStatus(StatusCode.ERROR)
            .recordException(e);
        instrumenter.end(context, request, null, e);
        request.setAttribute(OpenTelemetryServerResponseFilter.FINISHED, true);
        request.setAttribute(CONTINUE, true);
    }

    private boolean shouldExclude(@Nullable String path) {
        return pathExclusionTest != null && path != null && pathExclusionTest.test(path);
    }
}
