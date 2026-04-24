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
import io.micronaut.core.annotation.Order;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;

import static io.micronaut.tracing.opentelemetry.instrument.http.AbstractOpenTelemetryFilter.SERVER_PATH;

/**
 * Response hooks for HTTP server tracing that keep the span current for user response filters and
 * finish it after those filters run.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Internal
@ServerFilter(SERVER_PATH)
@Requires(beans = Tracer.class)
final class OpenTelemetryServerResponseFilter {

    private static final int RESPONSE_CONTEXT_ORDER = 1;
    private static final int REQUEST_CONTEXT_ORDER = -999;
    private static final int FINISH_ORDER = -1000;
    private static final String FINISHED = OpenTelemetryServerResponseFilter.class.getName() + "-finished";

    private final Instrumenter<HttpRequest<?>, Object> instrumenter;

    OpenTelemetryServerResponseFilter(@Named("micronautHttpServerTelemetryInstrumenter") Instrumenter<HttpRequest<?>, Object> instrumenter) {
        this.instrumenter = instrumenter;
    }

    @RequestFilter
    @Order(REQUEST_CONTEXT_ORDER)
    void propagateRequestContext(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
        Context context = request.getAttribute(OpenTelemetryServerFilter.CONTEXT, Context.class).orElse(null);
        if (context != null) {
            propagatedContext.add(new OpenTelemetryPropagationContext(context));
        }
    }

    @ResponseFilter
    @Order(RESPONSE_CONTEXT_ORDER)
    void propagateResponseContext(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
        Context context = request.getAttribute(OpenTelemetryServerFilter.CONTEXT, Context.class).orElse(null);
        if (context != null) {
            propagatedContext.add(new OpenTelemetryPropagationContext(context));
        }
    }

    @ResponseFilter
    @Order(FINISH_ORDER)
    void finishResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        if (request.getAttribute(FINISHED, Boolean.class).orElse(false)) {
            return;
        }
        Context context = request.getAttribute(OpenTelemetryServerFilter.CONTEXT, Context.class).orElse(null);
        if (context == null) {
            return;
        }
        request.setAttribute(FINISHED, true);
        response.getAttribute(HttpAttributes.EXCEPTION, Exception.class)
            .ifPresentOrElse(
                e -> onError(context, request, response, e), () -> {
                    if (response.status().getCode() >= 400) {
                        onError(context, request, response, null);
                    } else {
                        instrumenter.end(context, request, response, null);
                    }
                });
    }

    private void onError(Context context,
                         HttpRequest<?> request,
                         @Nullable MutableHttpResponse<?> response,
                         @Nullable Throwable e) {
        var span = Span.fromContext(context)
            .setStatus(StatusCode.ERROR);
        if (e != null) {
            span.recordException(e);
        }
        instrumenter.end(context, request, response, e);
    }
}
