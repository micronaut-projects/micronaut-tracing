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
package io.micronaut.tracing.brave.instrument.http;

import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.instrument.http.OpenTracingClientFilter;
import io.micronaut.tracing.instrument.http.TracingExclusionsConfiguration;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.CorePublisher;

import java.util.function.Predicate;

import static io.micronaut.tracing.instrument.http.AbstractOpenTracingFilter.CLIENT_PATH;

/**
 * Instruments outgoing HTTP requests.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(CLIENT_PATH)
@Requires(beans = HttpClientHandler.class)
@Replaces(OpenTracingClientFilter.class)
public class BraveTracingClientFilter implements HttpClientFilter {

    private final HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler;
    private final HttpTracing httpTracing;
    @Nullable
    private final Predicate<String> pathExclusionTest;

    /**
     * @param clientHandler the standard way to instrument HTTP client
     * @param httpTracing   the tracer for creation of span
     */
    public BraveTracingClientFilter(HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler,
                                    HttpTracing httpTracing) {
        this(clientHandler, httpTracing, null);
    }

    /**
     * Initialize tracing filter with clientHandler and httpTracing.
     *
     * @param clientHandler the standard way to instrument HTTP client
     * @param httpTracing   the tracer for creation of span
     * @param exclusionsConfiguration the {@link TracingExclusionsConfiguration}
     */
    @Inject
    public BraveTracingClientFilter(HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler,
                                    HttpTracing httpTracing,
                                    @Nullable TracingExclusionsConfiguration exclusionsConfiguration) {
        this.clientHandler = clientHandler;
        this.httpTracing = httpTracing;
        this.pathExclusionTest = exclusionsConfiguration == null ? null : exclusionsConfiguration.exclusionTest();
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                         ClientFilterChain chain) {
        Publisher<? extends HttpResponse<?>> requestPublisher = chain.proceed(request);
        if (shouldExclude(request.getPath())) {
            return requestPublisher;
        }

        if (requestPublisher instanceof CorePublisher) {
            return new HttpClientTracingCorePublisher(requestPublisher, request, clientHandler, httpTracing);
        }

        return new HttpClientTracingPublisher(requestPublisher, request, clientHandler, httpTracing);
    }

    private boolean shouldExclude(@Nullable String path) {
        return pathExclusionTest != null && path != null && pathExclusionTest.test(path);
    }
}
