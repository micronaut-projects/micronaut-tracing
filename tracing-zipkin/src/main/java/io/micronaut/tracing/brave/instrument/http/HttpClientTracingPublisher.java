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

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static io.micronaut.http.HttpAttributes.SERVICE_ID;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;
import static io.micronaut.tracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN;

/**
 * Handles HTTP client request tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@SuppressWarnings("PublisherImplementation")
class HttpClientTracingPublisher implements Publishers.MicronautPublisher<HttpResponse<?>> {

    protected final Publisher<? extends HttpResponse<?>> publisher;
    private final HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler;
    private final MutableHttpRequest<?> request;
    private final Tracer tracer;

    /**
     * @param publisher     the response publisher
     * @param request       an extended version of request that allows mutating
     * @param clientHandler the standardized way to instrument client
     * @param httpTracing   {@code HttpTracing}
     */
    HttpClientTracingPublisher(Publisher<? extends HttpResponse<?>> publisher,
                               MutableHttpRequest<?> request,
                               HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler,
                               HttpTracing httpTracing) {
        this.publisher = publisher;
        this.request = request;
        this.clientHandler = clientHandler;
        tracer = httpTracing.tracing().tracer();
    }

    @Override
    public void subscribe(Subscriber<? super HttpResponse<?>> actual) {

        HttpClientRequest httpClientRequest = mapRequest(request);
        Span span = clientHandler.handleSend(httpClientRequest);

        request.getAttribute(SERVICE_ID, String.class)
                .filter(StringUtils::isNotEmpty)
                .ifPresent(span::remoteServiceName);

        request.setAttribute(CURRENT_SPAN, span);

        try (SpanInScope ignored = tracer.withSpanInScope(span)) {
            doSubscribe(actual, span);
        }
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

    private static HttpClientResponse mapResponse(HttpRequest<?> request,
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

    /**
     * Do subscribe to the publisher.
     *
     * @param actual The actual subscriber
     * @param span   The span
     */
    @Internal
    protected void doSubscribe(Subscriber<? super HttpResponse<?>>  actual, Span span) {
        publisher.subscribe(new TracingHttpClientSubscriber(actual, span));
    }



    /**
     * The tracing subscriber.
     */
    @Internal
    protected class TracingHttpClientSubscriber implements Subscriber<HttpResponse<?>> {
        final Subscriber<? super HttpResponse<?>>  actual;
        final Span span;

        TracingHttpClientSubscriber(Subscriber<? super HttpResponse<?>>  actual, Span span) {
            this.actual = actual;
            this.span = span;
        }

        @Override
        public void onSubscribe(Subscription s) {
            try (SpanInScope ignored = tracer.withSpanInScope(span)) {
                actual.onSubscribe(s);
            }
        }

        @Override
        public void onNext(HttpResponse<?> response) {
            try (SpanInScope ignored = tracer.withSpanInScope(span)) {
                clientHandler.handleReceive(mapResponse(request, response, null), span);
                actual.onNext(response);
            }
        }

        @Override
        public void onError(Throwable error) {
            try (SpanInScope ignored = tracer.withSpanInScope(span)) {
                if (error instanceof HttpClientResponseException) {
                    HttpClientResponseException e = (HttpClientResponseException) error;
                    clientHandler.handleReceive(mapResponse(request, e.getResponse(), error), span);
                } else {
                    span.error(error);
                    span.finish();
                }

                actual.onError(error);
            }
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }


}
