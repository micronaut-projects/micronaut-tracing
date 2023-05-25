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
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.propagation.CurrentTraceContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.brave.BravePropagationContext;
import io.micronaut.tracing.opentracing.instrument.http.OpenTracingClientFilter;
import io.micronaut.tracing.opentracing.instrument.http.TracingExclusionsConfiguration;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.function.Predicate;

import static io.micronaut.http.HttpAttributes.SERVICE_ID;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;
import static io.micronaut.tracing.opentracing.instrument.http.AbstractOpenTracingFilter.CLIENT_PATH;
import static io.micronaut.tracing.opentracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN;

/**
 * Instruments outgoing HTTP requests.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Filter(CLIENT_PATH)
@Requires(beans = HttpClientHandler.class)
@Replaces(OpenTracingClientFilter.class)
public final class BraveTracingClientFilter implements HttpClientFilter {

    private final CurrentTraceContext currentTraceContext;
    private final HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler;
    @Nullable
    private final Predicate<String> pathExclusionTest;

    /**
     * Initialize tracing filter with clientHandler and httpTracing.
     *
     * @param currentTraceContext     The trace context
     * @param clientHandler           the standard way to instrument HTTP client
     * @param exclusionsConfiguration the {@link TracingExclusionsConfiguration}
     */
    public BraveTracingClientFilter(CurrentTraceContext currentTraceContext,
                                    HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler,
                                    @Nullable TracingExclusionsConfiguration exclusionsConfiguration) {
        this.currentTraceContext = currentTraceContext;
        this.clientHandler = clientHandler;
        this.pathExclusionTest = exclusionsConfiguration == null ? null : exclusionsConfiguration.exclusionTest();
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                         ClientFilterChain chain) {
        if (shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }

        HttpClientRequest httpClientRequest = mapRequest(request);
        Span span = clientHandler.handleSend(httpClientRequest);

        request.getAttribute(SERVICE_ID, String.class)
            .filter(StringUtils::isNotEmpty)
            .ifPresent(span::remoteServiceName);

        request.setAttribute(CURRENT_SPAN, span);

        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new BravePropagationContext(currentTraceContext, span.context()))
            .propagate()) {

            return Mono.from(ReactivePropagation.propagate(PropagatedContext.get(), chain.proceed(request)))
                .doOnNext(response -> clientHandler.handleReceive(mapResponse(request, response, null), span))
                .doOnError(throwable -> {
                    if (throwable instanceof HttpClientResponseException e) {
                        clientHandler.handleReceive(mapResponse(request, e.getResponse(), e), span);
                    } else {
                        span.error(throwable);
                    }
                });

        }
    }

    private boolean shouldExclude(@Nullable String path) {
        return pathExclusionTest != null && path != null && pathExclusionTest.test(path);
    }

    private HttpClientRequest mapRequest(MutableHttpRequest<?> request) {
        return new HttpClientRequest() {

            @Override
            public void header(String name, String value) {
                request.header(name, value);
            }

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

    private HttpClientResponse mapResponse(HttpRequest<?> request,
                                           HttpResponse<?> response,
                                           Throwable error) {
        return new HttpClientResponse() {

            @Override
            public Object unwrap() {
                return response;
            }

            @Override
            public Throwable error() {
                return error;
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
}
