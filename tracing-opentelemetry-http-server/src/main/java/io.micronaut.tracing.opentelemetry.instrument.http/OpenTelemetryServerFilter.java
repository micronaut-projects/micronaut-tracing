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
package io.micronaut.tracing.opentelemetry.instrument.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.opentelemetry.instrument.util.TracingExclusionsConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Predicate;

import static io.micronaut.tracing.opentelemetry.instrument.http.OpenTelemetryServerFilter.SERVER_PATH;

/**
 * An HTTP server instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 */
@Filter(SERVER_PATH)
@Requires(beans = Tracer.class)
public class OpenTelemetryServerFilter implements HttpServerFilter {

    public static final String SERVER_PATH = "${tracing.http.server.path:/**}";

    private static final String APPLIED = OpenTelemetryServerFilter.class.getName() + "-applied";
    private static final String CONTINUE = OpenTelemetryServerFilter.class.getName() + "-continue";

    private final Instrumenter<HttpRequest, HttpResponse> instrumenter;

    public static final String TAG_ERROR = "error";

    private final Predicate<String> pathExclusionTest;

    /**
     * Creates an HTTP server instrumentation filter.
     *
     * @param openTelemetry the openTelemetry
     */
    public OpenTelemetryServerFilter(OpenTelemetry openTelemetry) {
        this(openTelemetry, null);
    }

    /**
     * Creates an HTTP server instrumentation filter.
     *
     * @param openTelemetry    the openTelemetry
     * @param exclusionsConfig The {@link TracingExclusionsConfiguration}
     */
    @Inject
    public OpenTelemetryServerFilter(OpenTelemetry openTelemetry,
                                     @Nullable TracingExclusionsConfiguration exclusionsConfig) {
        pathExclusionTest = exclusionsConfig == null ? null : exclusionsConfig.exclusionTest();
        instrumenter = new MicronautHttpServerTelemetryBuilder(openTelemetry).build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        boolean applied = request.getAttribute(APPLIED, Boolean.class).orElse(false);
        boolean continued = request.getAttribute(CONTINUE, Boolean.class).orElse(false);
        if ((applied && !continued) || shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }

        request.setAttribute(APPLIED, true);

        Publisher<MutableHttpResponse<?>> requestPublisher = chain.proceed(request);

        return (Publishers.MicronautPublisher<MutableHttpResponse<?>>) actual -> {
            Context parentContext = Context.current();
            Context context = instrumenter.start(parentContext, request);

            try (Scope ignored = context.makeCurrent()) {
                requestPublisher.subscribe(new Subscriber<MutableHttpResponse<?>>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        try (Scope ignored = context.makeCurrent()) {
                            actual.onSubscribe(s);
                        }
                    }

                    @Override
                    public void onNext(MutableHttpResponse<?> response) {
                        try (Scope ignored = context.makeCurrent()) {
                            actual.onNext(response);
                        } finally {
                            instrumenter.end(context, request, response, null);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        try (Scope ignored = context.makeCurrent()) {
                            actual.onError(t);
                        } finally {
                            request.setAttribute(CONTINUE, true);
                            setErrorTags(Span.current(), t);
                            instrumenter.end(context, request, null, t);
                        }
                    }

                    @Override
                    public void onComplete() {
                        try (Scope ignored = context.makeCurrent()) {
                            actual.onComplete();
                        } finally {
                            instrumenter.end(context, request, null, null);
                        }
                    }
                });
            }
        };

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
        span.setAttribute(TAG_ERROR, message);
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
