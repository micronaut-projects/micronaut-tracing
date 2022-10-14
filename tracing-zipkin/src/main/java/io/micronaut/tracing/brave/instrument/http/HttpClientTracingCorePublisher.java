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
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * Handles HTTP client request tracing.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
@SuppressWarnings("PublisherImplementation")
class HttpClientTracingCorePublisher extends HttpClientTracingPublisher implements CorePublisher<HttpResponse<?>> {

    /**
     * @param publisher     the response publisher
     * @param request       an extended version of request that allows mutating
     * @param clientHandler the standardized way to instrument client
     * @param httpTracing   {@code HttpTracing}
     */
    HttpClientTracingCorePublisher(Publisher<? extends HttpResponse<?>> publisher, MutableHttpRequest<?> request, HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler, HttpTracing httpTracing) {
        super(publisher, request, clientHandler, httpTracing);
    }

    @Override
    public void subscribe(CoreSubscriber<? super HttpResponse<?>> subscriber) {
        subscribe((Subscriber) subscriber);
    }

    @Override
    protected void doSubscribe(Subscriber<? super HttpResponse<?>>  actual, Span span) {
        CoreSubscriber<? super HttpResponse<?>> coreActual = Operators.toCoreSubscriber(actual);
        publisher.subscribe(new TracingCoreSubscriber(actual, span,  coreActual.currentContext()));
    }

    private final class TracingCoreSubscriber extends TracingHttpClientSubscriber implements CoreSubscriber<HttpResponse<?>> {

        private final Context context;

        private TracingCoreSubscriber(Subscriber<? super HttpResponse<?>> actual,
                                      Span span,
                                      Context context) {
            super(actual, span);
            this.context = context;
        }

        @Override
        public Context currentContext() {
            return context;
        }
    }


}
