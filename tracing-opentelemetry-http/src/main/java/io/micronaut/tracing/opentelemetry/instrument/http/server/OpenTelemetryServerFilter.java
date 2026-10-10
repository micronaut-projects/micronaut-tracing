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
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.micronaut.tracing.opentelemetry.instrument.http.AbstractOpenTelemetryFilter;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryExclusionsConfiguration;
import io.micronaut.web.router.RouteAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.function.Predicate;

import static io.micronaut.http.filter.ServerFilterPhase.TRACING;

/**
 * An HTTP server instrumentation filter that uses Open Telemetry.
 * <p>
 * One filter in the {@link io.micronaut.http.filter.ServerFilterPhase#TRACING TRACING} phase, with two hooks:
 * <ul>
 *     <li>{@link #startSpan}, an around request filter, starts the server span and makes it the propagated
 *     context of the downstream filters (request and response) and of the route. It ends the span when the
 *     request is cancelled or the downstream fails.</li>
 *     <li>{@link #endSpan}, a response filter, ends the span with the response. A filter continuation
 *     yields the response once the route produced it, before any response filter runs, so the span is ended
 *     by the response filter of this filter, which runs after the response filters ordered after it (such
 *     as user filters at {@code TRACING.after()}, see #816).</li>
 * </ul>
 * The span is ended exactly once, whichever hook ends it.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Internal
@ServerFilter(AbstractOpenTelemetryFilter.SERVER_PATH)
@Requires(beans = Tracer.class)
public final class OpenTelemetryServerFilter extends AbstractOpenTelemetryFilter implements Ordered {

    /**
     * The request attribute holding the server span until it is ended. If the filter chain runs again for
     * the same request in the meantime (e.g. an error raised by the server while the route still runs), no
     * second span is started. Once the span is ended, a new pass starts a new span.
     */
    static final String SPAN = OpenTelemetryServerFilter.class.getName() + "-span";

    /**
     * The request attribute holding the OpenTelemetry {@link Context} of the server span of a WebSocket upgrade
     * request, kept after the span ended: the spans of the WebSocket handlers of the session are its children or
     * are linked to it.
     */
    public static final String WEBSOCKET_UPGRADE_CONTEXT = OpenTelemetryServerFilter.class.getName() + "-websocket-upgrade";

    private static final String WEBSOCKET = "websocket";

    private final Instrumenter<HttpRequest<?>, Object> instrumenter;

    /**
     * @param exclusionsConfig The {@link OpenTelemetryExclusionsConfiguration}
     * @param instrumenter     The {@link OpenTelemetryHttpServerConfig}
     * @deprecated The management endpoints are not excluded, use the injected constructor
     */
    @Deprecated(since = "8.4.0")
    public OpenTelemetryServerFilter(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig,
                                     @Named("micronautHttpServerTelemetryInstrumenter") Instrumenter<HttpRequest<?>, Object> instrumenter) {
        this(exclusionsConfig, List.of(), instrumenter);
    }

    /**
     * @param exclusionsConfig    The {@link OpenTelemetryExclusionsConfiguration}
     * @param managementEndpoints The paths of the management endpoints, excluded with the configured patterns
     * @param instrumenter        The {@link OpenTelemetryHttpServerConfig}
     */
    @Inject
    OpenTelemetryServerFilter(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig,
                              ManagementEndpointExclusions managementEndpoints,
                              @Named("micronautHttpServerTelemetryInstrumenter") Instrumenter<HttpRequest<?>, Object> instrumenter) {
        this(exclusionsConfig, managementEndpoints.patterns(), instrumenter);
    }

    private OpenTelemetryServerFilter(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig,
                                      List<String> managementEndpoints,
                                      Instrumenter<HttpRequest<?>, Object> instrumenter) {
        super(exclusionTest(exclusionsConfig, managementEndpoints));
        this.instrumenter = instrumenter;
    }

    @Nullable
    private static Predicate<String> exclusionTest(@Nullable OpenTelemetryExclusionsConfiguration exclusionsConfig,
                                                   List<String> managementEndpoints) {
        if (exclusionsConfig == null) {
            exclusionsConfig = new OpenTelemetryExclusionsConfiguration();
        }
        return exclusionsConfig.exclusionTest(managementEndpoints);
    }

    @Override
    public int getOrder() {
        return TRACING.order();
    }

    /**
     * Starts the server span.
     *
     * @param request           The request
     * @param propagatedContext The propagated context of the downstream
     * @param continuation      The continuation
     * @return The response publisher
     */
    @RequestFilter
    public Publisher<HttpResponse<?>> startSpan(HttpRequest<?> request,
                                                MutablePropagatedContext propagatedContext,
                                                FilterContinuation<Publisher<HttpResponse<?>>> continuation) {
        if (shouldExclude(request.getPath()) || request.getAttribute(SPAN).isPresent()) {
            return continuation.proceed();
        }

        Context parentContext = parentContext(propagatedContext);
        if (!instrumenter.shouldStart(parentContext, request)) {
            return continuation.proceed();
        }

        Context context = instrumenter.start(parentContext, request);
        ServerSpan span = new ServerSpan(instrumenter, request, context);
        request.setAttribute(SPAN, span);
        if (WEBSOCKET.equalsIgnoreCase(request.getHeaders().get(HttpHeaders.UPGRADE))) {
            request.setAttribute(WEBSOCKET_UPGRADE_CONTEXT, context);
        }
        propagatedContext.add(new OpenTelemetryPropagationContext(context));
        // A reactive continuation rather than a CompletionStage one: the downstream of a stage continuation
        // runs eagerly, outside of the subscription of the upstream filters, so the Reactor context written
        // by an upstream reactive filter (contextWrite) would no longer reach the route, e.g. the
        // ReactorContext of a suspended Kotlin route.
        return Mono.from(continuation.proceed())
            .doOnNext(span::responded)
            .doOnError(span::failed)
            .doOnCancel(span::cancelled);
    }

    /**
     * Ends the server span with the response, after the response filters ordered after this filter.
     *
     * @param request  The request
     * @param response The response
     */
    @ResponseFilter
    public void endSpan(HttpRequest<?> request, HttpResponse<?> response) {
        if (request.getAttribute(SPAN).orElse(null) instanceof ServerSpan span) {
            span.end(response);
        }
    }

    /**
     * Resolves the parent context of the server span. Only a context propagated by Micronaut is trusted.
     * The thread-local {@link Context#current()} is deliberately ignored: a server request starts on an
     * event-loop thread, and a scope opened with {@code Span.makeCurrent()} by a previous request and closed
     * on another thread (e.g. after a Reactor boundary) leaves that request's context, including its server
     * span, current on the event-loop thread. Using it would suppress or wrongly parent the next server span
     * (see issue #475). A remote parent is still extracted from the request headers by the instrumenter.
     *
     * @param propagatedContext The propagated context of the request
     * @return the parent context
     */
    private static Context parentContext(MutablePropagatedContext propagatedContext) {
        OpenTelemetryPropagationContext element = propagatedContext.getContext().findOrNull(OpenTelemetryPropagationContext.class);
        return element == null ? Context.root() : element.context();
    }

    /**
     * A server span, ended exactly once: with the response by the response filter, an exception of the
     * route and a status of 400 or above marking it as an error; with the failure if the downstream fails;
     * or without a response if the request is cancelled before the response, leaving the status unset (the
     * server did not fail, as for the HTTP client filter).
     */
    static final class ServerSpan {

        private static final VarHandle ENDED;

        static {
            try {
                ENDED = MethodHandles.lookup().findVarHandle(ServerSpan.class, "ended", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final Instrumenter<HttpRequest<?>, Object> instrumenter;
        private final HttpRequest<?> request;
        private final Context context;
        @SuppressWarnings("unused") // accessed through ENDED
        private volatile boolean ended;
        private volatile boolean responded;

        ServerSpan(Instrumenter<HttpRequest<?>, Object> instrumenter, HttpRequest<?> request, Context context) {
            this.instrumenter = instrumenter;
            this.request = request;
            this.context = context;
        }

        /**
         * The downstream produced the response: the span is left to the response filter, also if the
         * subscriber cancels afterwards.
         *
         * @param response The response
         */
        void responded(HttpResponse<?> response) {
            responded = true;
        }

        /**
         * Ends the span when the downstream fails.
         *
         * @param error The failure
         */
        void failed(Throwable error) {
            responded = true;
            if (tryEnd()) {
                instrumenter.end(context, request, null, markError(error));
            }
        }

        /**
         * Ends the span when the request is cancelled (e.g. the client disconnected) before a response was
         * produced, because the response filter that normally ends it will not run.
         */
        void cancelled() {
            if (!responded && tryEnd()) {
                instrumenter.end(context, request, null, null);
            }
        }

        void end(HttpResponse<?> response) {
            if (!tryEnd()) {
                return;
            }
            Throwable exception = RouteAttributes.getException(response).orElse(null);
            if (exception != null) {
                instrumenter.end(context, request, response, markError(exception));
            } else if (response.code() >= 400) {
                markError(null);
                instrumenter.end(context, request, response, null);
            } else {
                instrumenter.end(context, request, response, null);
            }
        }

        @Nullable
        private Throwable markError(@Nullable Throwable error) {
            Span span = Span.fromContext(context).setStatus(StatusCode.ERROR);
            if (error != null) {
                span.recordException(error);
            }
            return error;
        }

        private boolean tryEnd() {
            if (ENDED.compareAndSet(this, false, true)) {
                // ended: if the filter runs again for this request, it starts a new span
                request.removeAttribute(SPAN, ServerSpan.class);
                return true;
            }
            return false;
        }
    }
}
