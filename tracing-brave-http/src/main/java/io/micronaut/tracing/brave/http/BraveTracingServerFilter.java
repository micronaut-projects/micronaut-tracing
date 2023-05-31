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
package io.micronaut.tracing.brave.http;

import brave.Span;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.propagation.CurrentTraceContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.brave.BravePropagationContext;
import io.micronaut.tracing.opentracing.instrument.http.OpenTracingServerFilter;
import io.micronaut.tracing.opentracing.instrument.http.TracingExclusionsConfiguration;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Predicate;

import static io.micronaut.http.HttpAttributes.EXCEPTION;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;
import static io.micronaut.http.filter.ServerFilterPhase.TRACING;
import static io.micronaut.tracing.opentracing.instrument.http.AbstractOpenTracingFilter.SERVER_PATH;

/**
 * Instruments incoming HTTP requests.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Filter(SERVER_PATH)
@Requires(beans = HttpServerHandler.class)
@Replaces(OpenTracingServerFilter.class)
public final class BraveTracingServerFilter implements HttpServerFilter {

    private final CurrentTraceContext currentTraceContext;
    private final HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler;

    @Nullable
    private final Predicate<String> pathExclusionTest;

    /**
     * @param currentTraceContext     The trace context
     * @param serverHandler           the {@code HttpServerHandler} instance
     * @param exclusionsConfiguration the {@link TracingExclusionsConfiguration}
     */
    @Inject
    public BraveTracingServerFilter(CurrentTraceContext currentTraceContext,
                                    HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler,
                                    @Nullable TracingExclusionsConfiguration exclusionsConfiguration) {
        this.currentTraceContext = currentTraceContext;
        this.serverHandler = serverHandler;
        this.pathExclusionTest = exclusionsConfiguration == null ? null : exclusionsConfiguration.exclusionTest();
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request,
                                                      ServerFilterChain chain) {

        if (shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }
        HttpServerRequest httpServerRequest = mapRequest(request);
        Span span = serverHandler.handleReceive(httpServerRequest);

        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new BravePropagationContext(currentTraceContext, span.context()))
            .propagate()) {

            PropagatedContext propagatedContext = PropagatedContext.get();
            return Mono.from(chain.proceed(request))
                .doOnNext(response -> {
                    final Optional<Throwable> throwable = response.getAttribute(EXCEPTION, Throwable.class);
                    if (throwable.isPresent()) {
                        int statusCode = 500;
                        Throwable error = throwable.get();
                        if (error instanceof HttpStatusException) {
                            statusCode = ((HttpStatusException) error).getStatus().getCode();
                        }
                        serverHandler.handleSend(mapResponse(request, statusCode, error), span);
                    } else {
                        serverHandler.handleSend(mapResponse(request, response), span);
                    }
                })
                .doOnError(throwable -> span.error(throwable).finish())
                .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext));
        }
    }

    @Override
    public int getOrder() {
        return TRACING.order();
    }

    private boolean shouldExclude(@Nullable String path) {
        return pathExclusionTest != null && path != null && pathExclusionTest.test(path);
    }

    private HttpServerRequest mapRequest(HttpRequest<?> request) {
        return new HttpServerRequest() {

            @Override
            public String method() {
                return request.getMethodName();
            }

            @Override
            public String path() {
                return request.getPath();
            }

            @Override
            public String url() {
                return request.getUri().toString();
            }

            @Override
            public String header(String name) {
                return request.getHeaders().get(name);
            }

            @Override
            public Object unwrap() {
                return request;
            }
        };
    }

    private HttpServerResponse mapResponse(HttpRequest<?> request, HttpResponse<?> response) {
        return new HttpServerResponse() {

            @Override
            public Object unwrap() {
                return response;
            }

            @Override
            public String method() {
                return request.getMethodName();
            }

            @Override
            public String route() {
                return request.getAttribute(URI_TEMPLATE, String.class).orElse(null);
            }

            @Override
            public int statusCode() {
                return response.getStatus().getCode();
            }
        };
    }

    private HttpServerResponse mapResponse(HttpRequest<?> request, int statusCode, Throwable error) {
        return new HttpServerResponse() {

            @Override
            public Throwable error() {
                return error;
            }

            @Override
            public Object unwrap() {
                return this;
            }

            @Override
            public String method() {
                return request.getMethodName();
            }

            @Override
            public String route() {
                return request.getAttribute(URI_TEMPLATE, String.class).orElse(null);
            }

            @Override
            public int statusCode() {
                return statusCode;
            }
        };
    }
}
