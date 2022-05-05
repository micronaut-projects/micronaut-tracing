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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
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

import static io.micronaut.tracing.opentelemetry.instrument.http.OpenTelemetryClientFilter.CLIENT_PATH;

/**
 * An HTTP client instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 */
@Filter(CLIENT_PATH)
@Requires(beans = Tracer.class)
public class OpenTelemetryClientFilter implements HttpClientFilter {

    public static final String CLIENT_PATH = "${tracing.http.client.path:/**}";
    public static final String TAG_ERROR = "error";

    private final Predicate<String> pathExclusionTest;

    private final Instrumenter<MutableHttpRequest, HttpResponse> instrumenter;

    /**
     * @param openTelemetry the openTelemetry
     */
    public OpenTelemetryClientFilter(OpenTelemetry openTelemetry) {
        this(openTelemetry, null);
    }

    /**
     * Initialize the open tracing client filter with tracer and exclusion configuration.
     *
     * @param openTelemetry    the openTelemetry
     * @param exclusionsConfig The {@link TracingExclusionsConfiguration}
     */
    @Inject
    public OpenTelemetryClientFilter(OpenTelemetry openTelemetry,
                                     @Nullable TracingExclusionsConfiguration exclusionsConfig) {
        pathExclusionTest = exclusionsConfig != null ? exclusionsConfig.exclusionTest() : null;

        instrumenter = new MicronautHttpClientTelemetryBuilder(openTelemetry).build();

    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                         ClientFilterChain chain) {

        Publisher<? extends HttpResponse<?>> requestPublisher = chain.proceed(request);

        if (shouldExclude(request.getPath())) {
            return requestPublisher;
        }

        Context parentContext = Context.current();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return requestPublisher;
        }

        return (Publishers.MicronautPublisher<HttpResponse<?>>) actual -> {
            Context context = instrumenter.start(parentContext, request);

            try (Scope ignored = context.makeCurrent()) {
                requestPublisher.subscribe(new Subscriber<HttpResponse<?>>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        try (Scope ignored = context.makeCurrent()) {
                            actual.onSubscribe(s);
                        }
                    }

                    @Override
                    public void onNext(HttpResponse<?> response) {
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
