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
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.filter.FilterContinuation;
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import static io.micronaut.http.HttpAttributes.INVOCATION_CONTEXT;

/**
 * An HTTP client instrumentation filter that uses Open Telemetry.
 * <p>
 * An around filter: it starts the client span (which injects the trace headers into the request), makes it
 * the current context of the downstream filters and the request, and ends it exactly once, when the response
 * or failure is received or when the request is cancelled.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Internal
@ClientFilter(AbstractOpenTelemetryFilter.CLIENT_PATH)
public final class OpenTelemetryClientFilter extends AbstractOpenTelemetryFilter {

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

    /**
     * Traces the request.
     *
     * @param request      The request
     * @param continuation The continuation
     * @return The response stage, completed after the span ended
     */
    @RequestFilter
    public CompletionStage<HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                     FilterContinuation<CompletionStage<HttpResponse<?>>> continuation) {
        if (shouldExclude(request.getPath())) {
            return continuation.proceed();
        }

        Context parentContext = Context.current();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return continuation.proceed();
        }

        // Some instrumenter implementations may choose not to create telemetry state for a request.
        // In that case, fail open by proceeding without making a context current or ending a span.
        Context context = instrumenter.start(parentContext, request);
        if (context == null) {
            return continuation.proceed();
        }

        try (Scope ignored = context.makeCurrent()) {
            handleContinueSpan(request);
        }

        // The span is current for the downstream only: the propagated context of the filter is left
        // unchanged, and the returned stage completes with the parent context current (the stages of the
        // continuation complete in the context of the downstream), so the code consuming the response
        // does not run with the client span as its current context.
        PropagatedContext callerContext = PropagatedContext.getOrEmpty();
        // a callback rather than a scope: the scoped-value propagation mode doesn't support scopes
        CompletionStage<HttpResponse<?>> downstream = callerContext
            .plus(new OpenTelemetryPropagationContext(context))
            .propagate(continuation::proceed);
        return new ClientSpan(instrumenter, request, context, callerContext.plus(new OpenTelemetryPropagationContext(parentContext)))
            .endOnCompletion(downstream);
    }

    private void handleContinueSpan(MutableHttpRequest<?> request) {
        Object invocationContext = request.getAttribute(INVOCATION_CONTEXT).orElse(null);
        if (invocationContext instanceof MethodInvocationContext<?, ?> context) {
            if (context.hasAnnotation(ContinueSpan.class)) {
                AbstractOpenTelemetryTraceInterceptor.tagArguments(context);
            }
        }
    }

    /**
     * The stage returned by the filter: it ends the client span exactly once, when the downstream completes
     * or when it is cancelled, and only then completes with the outcome of the downstream, in the given
     * completion context. A failure marks the span as an error, with the response of an
     * {@link HttpResponseProvider} failure (e.g. an error status). A cancellation (e.g. the subscriber of the
     * client cancelled) ends it without a response and leaves the status unset, then cancels the downstream.
     */
    private static final class ClientSpan extends CompletableFuture<HttpResponse<?>>
        implements BiConsumer<HttpResponse<?>, Throwable> {

        private static final VarHandle ENDED;

        static {
            try {
                ENDED = MethodHandles.lookup().findVarHandle(ClientSpan.class, "ended", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final Instrumenter<MutableHttpRequest<?>, Object> instrumenter;
        private final MutableHttpRequest<?> request;
        private final Context context;
        private final PropagatedContext completionContext;
        @Nullable
        private CompletableFuture<HttpResponse<?>> downstream;
        @SuppressWarnings("unused") // accessed through ENDED
        private volatile boolean ended;

        ClientSpan(Instrumenter<MutableHttpRequest<?>, Object> instrumenter,
                   MutableHttpRequest<?> request,
                   Context context,
                   PropagatedContext completionContext) {
            this.instrumenter = instrumenter;
            this.request = request;
            this.context = context;
            this.completionContext = completionContext;
        }

        CompletionStage<HttpResponse<?>> endOnCompletion(CompletionStage<HttpResponse<?>> stage) {
            CompletableFuture<HttpResponse<?>> future = stage.toCompletableFuture();
            downstream = future;
            future.whenComplete(this);
            return this;
        }

        @Override
        public void accept(@Nullable HttpResponse<?> response, @Nullable Throwable error) {
            Throwable failure = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (tryEnd()) {
                if (failure instanceof CancellationException) {
                    instrumenter.end(context, request, null, null);
                } else if (failure != null) {
                    Span span = Span.fromContext(context);
                    span.recordException(failure);
                    span.setStatus(StatusCode.ERROR);
                    HttpResponse<?> errorResponse = failure instanceof HttpResponseProvider provider ? provider.getResponse() : null;
                    instrumenter.end(context, request, errorResponse, failure);
                } else {
                    instrumenter.end(context, request, response, null);
                }
            }
            if (completionContext.isBound()) {
                complete(response, failure);
            } else {
                completionContext.propagate(() -> complete(response, failure));
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                if (tryEnd()) {
                    instrumenter.end(context, request, null, null);
                }
                CompletableFuture<HttpResponse<?>> future = downstream;
                if (future != null) {
                    future.cancel(mayInterruptIfRunning);
                }
            }
            return cancelled;
        }

        private void complete(@Nullable HttpResponse<?> response, @Nullable Throwable failure) {
            if (failure != null) {
                completeExceptionally(failure);
            } else {
                complete(response);
            }
        }

        private boolean tryEnd() {
            return ENDED.compareAndSet(this, false, true);
        }
    }
}
