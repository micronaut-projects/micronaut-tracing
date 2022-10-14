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
package io.micronaut.tracing.opentelemetry.instrument.util;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * A reactor publisher that traces.
 *
 * @param <T> the type of element signaled
 * @param <R> the type of request element
 * @author Nemanja Mikic
 * @since 4.2.0
 */
public class OpenTelemetryCorePublisher<T, R> extends OpenTelemetryPublisher<T, R> implements CorePublisher<T> {

    /**
     * @param publisher the target publisher
     * @param instrumenter the instrumenter
     * @param parentContext the context from a parent
     * @param request the request object
     * @param observer the tracing observer
     */
    public OpenTelemetryCorePublisher(Publisher<T> publisher, Instrumenter<R, Object> instrumenter, io.opentelemetry.context.Context parentContext, R request, OpenTelemetryObserver<T> observer) {
        super(publisher, instrumenter, parentContext, request, observer);
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> subscriber) {
        subscribe((Subscriber) subscriber);
    }

    @Override
    protected void doSubscribe(Subscriber<? super T> actual, io.opentelemetry.context.Context context) {
        CoreSubscriber<? super T> coreActual = Operators.toCoreSubscriber(actual);
        publisher.subscribe(new TracingCoreSubscriber(actual, context,  coreActual.currentContext()));
    }

    private final class TracingCoreSubscriber extends TracingSubscriber implements CoreSubscriber<T> {

        private final Context reactorContext;

        private TracingCoreSubscriber(Subscriber<? super T> actual,
                                      io.opentelemetry.context.Context openTelemetryContext,
                                      Context reactorContext) {
            super(actual, openTelemetryContext);
            this.reactorContext = reactorContext;
        }

        @Override
        public Context currentContext() {
            return reactorContext;
        }
    }
}
