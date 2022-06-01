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
package io.micronaut.tracing.instrument.http;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.HttpFilter;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;

import java.util.Optional;
import java.util.function.Predicate;

import static io.micronaut.http.HttpAttributes.ERROR;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;

/**
 * Abstract filter used for Open Tracing based HTTP tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractOpenTracingFilter implements HttpFilter {

    public static final String CLIENT_PATH = "${tracing.http.client.path:/**}";
    public static final String SERVER_PATH = "${tracing.http.server.path:/**}";
    public static final String TAG_METHOD = "http.method";
    public static final String TAG_PATH = "http.path";
    public static final String TAG_ERROR = "error";
    public static final String TAG_HTTP_STATUS_CODE = "http.status_code";
    public static final String TAG_HTTP_CLIENT = "http.client";
    public static final String TAG_HTTP_SERVER = "http.server";

    private static final int HTTP_SUCCESS_CODE_UPPER_LIMIT = 299;

    protected final Tracer tracer;

    @Nullable
    private final Predicate<String> pathExclusionTest;

    /**
     * Configure tracer in the filter for span creation and propagation across arbitrary transports.
     *
     * @param tracer the tracer
     */
    protected AbstractOpenTracingFilter(Tracer tracer) {
        this(tracer, null);
    }

    /**
     * Configure tracer in the filter for span creation and propagation across
     * arbitrary transports.
     *
     * @param tracer            the tracer
     * @param pathExclusionTest the predicate for excluding URI paths from tracing
     */
    protected AbstractOpenTracingFilter(Tracer tracer,
                                     @Nullable Predicate<String> pathExclusionTest) {
        this.tracer = tracer;
        this.pathExclusionTest = pathExclusionTest;
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
        HttpStatus status = response.getStatus();
        int code = status.getCode();
        if (code > HTTP_SUCCESS_CODE_UPPER_LIMIT) {
            span.setTag(TAG_HTTP_STATUS_CODE, code);
            span.setTag(TAG_ERROR, status.getReason());
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
        span.setTag(TAG_ERROR, message);
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
    protected SpanBuilder newSpan(HttpRequest<?> request, SpanContext spanContext) {
        String spanName = resolveSpanName(request);
        String path = request.getPath();

        SpanBuilder spanBuilder = tracer.buildSpan(spanName).asChildOf(spanContext);

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
}
