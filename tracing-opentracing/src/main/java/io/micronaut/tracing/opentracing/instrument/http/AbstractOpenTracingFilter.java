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
package io.micronaut.tracing.opentracing.instrument.http;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.tracing.opentracing.OpenTracingPropagationContext;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.log.Fields;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.micronaut.http.HttpAttributes.ERROR;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;

/**
 * Abstract filter used for Open Tracing based HTTP tracing.
 *
 * @author graemerocher
 * @since 1.0
 * @deprecated OpenTracing is archived. Use Micronaut Tracing OpenTelemetry HTTP instead. See the
 * "Migrating from OpenTracing" section of the user guide.
 */
@Deprecated(since = "8.4.0", forRemoval = true)
@Internal
public abstract sealed class AbstractOpenTracingFilter implements HttpFilter
    permits OpenTracingClientFilter, OpenTracingServerFilter {

    public static final String CLIENT_PATH = "${tracing.http.client.path:/**}";
    public static final String SERVER_PATH = "${tracing.http.server.path:/**}";
    public static final String TAG_METHOD = "http.method";
    public static final String TAG_PATH = "http.path";
    public static final String TAG_ERROR = "error";
    public static final String TAG_HTTP_STATUS_CODE = "http.status_code";
    public static final String TAG_HTTP_CLIENT = "http.client";
    public static final String TAG_HTTP_SERVER = "http.server";

    /**
     * The property that switches the {@value #TAG_ERROR} tag to the boolean value defined by the OpenTracing
     * semantic conventions. The error message is then recorded as a span log event instead.
     * Defaults to {@code false}, which keeps the error message as the value of the tag.
     *
     * @since 8.4.0
     */
    public static final String PROPERTY_BOOLEAN_ERROR_TAG = "tracing.opentracing.boolean-error-tag";

    private static final int HTTP_SUCCESS_CODE_UPPER_LIMIT = 299;
    private static final String LOG_EVENT_ERROR = "error";

    protected final Tracer tracer;

    protected final ConversionService conversionService;

    @Nullable
    private final Predicate<String> pathExclusionTest;

    private boolean booleanErrorTag;

    /**
     * Configure tracer in the filter for span creation and propagation across
     * arbitrary transports.
     *
     * @param tracer            the tracer
     * @param conversionService the {@code ConversionService} instance
     * @param pathExclusionTest the predicate for excluding URI paths from tracing
     */
    protected AbstractOpenTracingFilter(Tracer tracer,
                                        ConversionService conversionService,
                                        @Nullable Predicate<String> pathExclusionTest) {
        this.tracer = tracer;
        this.conversionService = conversionService;
        this.pathExclusionTest = pathExclusionTest;
    }

    /**
     * Whether the {@value #TAG_ERROR} tag holds the boolean {@code true}, as defined by the OpenTracing
     * semantic conventions, with the error details recorded as a span log event, instead of the error message.
     *
     * @param booleanErrorTag {@code true} to use a boolean error tag
     * @since 8.4.0
     */
    @Inject
    public void setBooleanErrorTag(@Value("${" + PROPERTY_BOOLEAN_ERROR_TAG + ":false}") boolean booleanErrorTag) {
        this.booleanErrorTag = booleanErrorTag;
    }

    /**
     * Sets the response tags.
     *
     * @param request  the request
     * @param response the response
     * @param span     the span
     */
    protected void setResponseTags(HttpRequest<?> request,
                                   HttpResponse<?> response,
                                   Span span) {
        int code = response.code();
        if (code > HTTP_SUCCESS_CODE_UPPER_LIMIT) {
            span.setTag(TAG_HTTP_STATUS_CODE, code);
            String reason = HttpStatus.getDefaultReason(code);
            if (booleanErrorTag) {
                span.setTag(TAG_ERROR, true);
                span.log(Map.of(Fields.EVENT, LOG_EVENT_ERROR, Fields.MESSAGE, reason != null ? reason : String.valueOf(code)));
            } else {
                span.setTag(TAG_ERROR, reason);
            }
        }
        request.getAttribute(ERROR, Throwable.class)
                .ifPresent(error -> setErrorTags(span, error));
    }

    /**
     * Sets the error tags to use on the span.
     *
     * @param span  the span
     * @param error the error
     */
    protected void setErrorTags(Span span, Throwable error) {
        if (error == null) {
            return;
        }

        String message = error.getMessage();
        if (message == null) {
            message = error.getClass().getSimpleName();
        }
        if (booleanErrorTag) {
            span.setTag(TAG_ERROR, true);
            span.log(Map.of(
                Fields.EVENT, LOG_EVENT_ERROR,
                Fields.ERROR_KIND, error.getClass().getName(),
                Fields.ERROR_OBJECT, error,
                Fields.MESSAGE, message
            ));
        } else {
            span.setTag(TAG_ERROR, message);
        }
    }

    /**
     * Resolve the span name to use for the request.
     *
     * @param request the request
     * @return the span name
     */
    protected String resolveSpanName(HttpRequest<?> request) {
        Optional<String> route = request.getAttribute(URI_TEMPLATE, String.class);
        return route.map(s -> request.getMethodName() + ' ' + s)
                .orElse(request.getMethodName() + ' ' + request.getPath());
    }

    /**
     * Creates a new span for the given request and span context.
     *
     * @param request     the request
     * @param spanContext the span context
     * @return the span builder
     */
    protected SpanBuilder newSpan(HttpRequest<?> request, @Nullable SpanContext spanContext) {
        String spanName = resolveSpanName(request);
        String path = request.getPath();

        SpanBuilder spanBuilder = tracer.buildSpan(spanName);
        if (spanContext != null) {
            spanBuilder.asChildOf(spanContext);
        } else {
            spanBuilder.ignoreActiveSpan();
        }

        spanBuilder.withTag(TAG_METHOD, request.getMethodName());
        spanBuilder.withTag(TAG_PATH, path);

        return spanBuilder;
    }

    /**
     * Tests if the defined path should be excluded from tracing.
     *
     * @param path the path to test
     * @return {@code true} if the path should be excluded
     */
    protected boolean shouldExclude(@Nullable String path) {
        return pathExclusionTest != null && path != null && pathExclusionTest.test(path);
    }

    /**
     * Creates the propagated context for the current OpenTracing span.
     *
     * @param span The span to propagate
     * @return The propagated context
     */
    protected PropagatedContext propagationContext(Span span) {
        return OpenTracingPropagationContext.withSpan(PropagatedContext.getOrEmpty(), tracer, span);
    }

    /**
     * Wraps the given source so that only the synchronous part of its subscription
     * (invoking the filter chain and subscribing downstream) runs with the given
     * context propagated on the subscribing thread. The thread-local state is restored
     * as soon as {@code subscribe} returns, so it is never held open across asynchronous
     * boundaries. Asynchronous signals rely on the Reactor context instead, which
     * carries the propagated context via {@link ReactorPropagation}.
     *
     * @param propagatedContext The context to propagate
     * @param source            The source publisher, invoked lazily on subscription
     * @param <T>               The emitted type
     * @return the wrapped publisher
     */
    protected static <T> Mono<T> propagateOnSubscribe(PropagatedContext propagatedContext,
                                                      Supplier<Mono<T>> source) {
        Mono<T> deferred = Mono.defer(source)
            .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext));
        return Mono.fromDirect(subscriber -> propagatedContext.propagate(() -> deferred.subscribe(subscriber)));
    }
}
