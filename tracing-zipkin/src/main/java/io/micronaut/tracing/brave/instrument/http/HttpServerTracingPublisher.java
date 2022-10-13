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
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.tracing.instrument.util.ScopePropagationPublisher;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;

import static io.micronaut.http.HttpAttributes.EXCEPTION;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;
import static io.micronaut.tracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN;

/**
 * Handles HTTP client server tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
public class HttpServerTracingPublisher implements Publishers.MicronautPublisher<MutableHttpResponse<?>> {

    protected final Publisher<MutableHttpResponse<?>> publisher;
    private final HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler;
    private final HttpRequest<?> request;
    private final Tracer tracer;
    private final io.opentracing.Tracer openTracer;
    private final Span initialSpan;

    /**
     * Construct a publisher to handle HTTP client request tracing.
     *
     * @param publisher     the response publisher
     * @param request       an extended version of request that allows mutating
     * @param serverHandler the standard way to instrument client
     * @param httpTracing   {@code HttpTracing}
     * @param openTracer    the Open Tracing instance
     * @param initialSpan   the initial span
     */
    HttpServerTracingPublisher(Publisher<MutableHttpResponse<?>> publisher,
                               HttpRequest<?> request,
                               HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler,
                               HttpTracing httpTracing,
                               io.opentracing.Tracer openTracer,
                               Span initialSpan) {
        this.publisher = publisher;
        this.request = request;
        this.initialSpan = initialSpan;
        this.serverHandler = serverHandler;
        this.openTracer = openTracer;
        tracer = httpTracing.tracing().tracer();
    }

    @Override
    public void subscribe(Subscriber<? super MutableHttpResponse<?>> actual) {
        Span span = initialSpan;
        request.setAttribute(CURRENT_SPAN, span);
        try (SpanInScope ignored = tracer.withSpanInScope(span)) {
            doSubscribe(span, actual);
        }
    }

    /**
     * Do subscribe to the publisher.
     *
     * @param actual The actual subscriber
     * @param span   The span
     */
    @Internal
    protected void doSubscribe(Span span, Subscriber<? super MutableHttpResponse<?>> actual) {
        publisher.subscribe(new TracingSubscriber(span, actual));
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

    @SuppressWarnings("SubscriberImplementation")
    protected class TracingSubscriber implements Subscriber<MutableHttpResponse<?>> {

        private final Subscriber<? super MutableHttpResponse<?>> actual;
        private final Span span;

        protected TracingSubscriber(Span span,
                                    Subscriber<? super MutableHttpResponse<?>> actual) {
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
        public void onNext(MutableHttpResponse<?> response) {
            try (SpanInScope ignored = tracer.withSpanInScope(span)) {
                Optional<?> body = response.getBody();
                if (body.isPresent()) {
                    Object o = body.get();
                    if (Publishers.isConvertibleToPublisher(o)) {
                        Class<?> type = o.getClass();
                        Publisher<?> resultPublisher = Publishers.convertPublisher(o, Publisher.class);
                        Publisher<?> scopedPublisher = new ScopePropagationPublisher(
                                resultPublisher,
                                openTracer,
                                openTracer.activeSpan()
                        );

                        response.body(Publishers.convertPublisher(scopedPublisher, type));
                    }
                }

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
                actual.onNext(response);
            }
        }

        @Override
        public void onError(Throwable error) {
            actual.onError(error);
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }
}
