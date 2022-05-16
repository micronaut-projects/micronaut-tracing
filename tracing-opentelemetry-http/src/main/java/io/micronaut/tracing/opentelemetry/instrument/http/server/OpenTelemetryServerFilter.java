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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.opentelemetry.instrument.http.AbstractOpenTracingFilter;
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

import static io.micronaut.tracing.opentelemetry.instrument.http.server.OpenTelemetryServerFilter.SERVER_PATH;

/**
 * An HTTP server instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 */
@Filter(SERVER_PATH)
@Requires(beans = Tracer.class)
public class OpenTelemetryServerFilter extends AbstractOpenTracingFilter implements HttpServerFilter {

    private static final String APPLIED = OpenTelemetryServerFilter.class.getName() + "-applied";
    private static final String CONTINUE = OpenTelemetryServerFilter.class.getName() + "-continue";

    private final Instrumenter<HttpRequest, HttpResponse> instrumenter;

    /**
     * @param openTelemetry the openTelemetry
     */
    public OpenTelemetryServerFilter(OpenTelemetry openTelemetry) {
        this(openTelemetry, null);
    }

    /**
     * @param openTelemetry    the openTelemetry
     * @param exclusionsConfig The {@link TracingExclusionsConfiguration}
     */
    @Inject
    public OpenTelemetryServerFilter(OpenTelemetry openTelemetry,
                                     @Nullable TracingExclusionsConfiguration exclusionsConfig) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
        instrumenter = new MicronautHttpServerTelemetryBuilder(openTelemetry).build();
    }

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
}
