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
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * Handles HTTP client server tracing.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
public class HttpServerTracingCorePublisher extends HttpServerTracingPublisher implements CorePublisher<MutableHttpResponse<?>> {

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
    HttpServerTracingCorePublisher(Publisher<MutableHttpResponse<?>> publisher, HttpRequest<?> request, HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler, HttpTracing httpTracing, io.opentracing.Tracer openTracer, Span initialSpan, ConversionService conversionService) {
        super(publisher, request, serverHandler, httpTracing, openTracer, initialSpan, conversionService);
    }

    @Override
    public void subscribe(CoreSubscriber<? super MutableHttpResponse<?>> subscriber) {
        subscribe((Subscriber) subscriber);
    }

    @Override
    protected void doSubscribe(Span span, Subscriber<? super MutableHttpResponse<?>> actual) {
        CoreSubscriber<? super MutableHttpResponse<?>> coreActual = Operators.toCoreSubscriber(actual);
        publisher.subscribe(new TracingCoreSubscriber(actual, span,  coreActual.currentContext()));
    }

    private final class TracingCoreSubscriber extends TracingSubscriber implements CoreSubscriber<MutableHttpResponse<?>> {

        private final Context context;

        private TracingCoreSubscriber(Subscriber<? super MutableHttpResponse<?>> actual,
                                      Span span,
                                      Context context) {
            super(span, actual);
            this.context = context;
        }

        @Override
        public Context currentContext() {
            return context;
        }
    }
}
