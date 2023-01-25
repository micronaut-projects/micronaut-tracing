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
package io.micronaut.tracing.instrument.util;

import io.micronaut.core.convert.ConversionService;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import org.reactivestreams.Subscriber;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * The tracing publisher that supports Reactor's context.
 *
 * @param <T> the type of element signaled
 * @author Denis Stepanov
 */
@SuppressWarnings("PublisherImplementation")
public class TracingCorePublisher<T> extends TracingPublisher<T> implements CorePublisher<T> {

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher         the target publisher
     * @param tracer            the tracer
     * @param conversionService the {@code ConversionService} instance
     * @param operationName     the operation name that should be started
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                String operationName,
                                ConversionService conversionService) {
        super(publisher, tracer, operationName, conversionService);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher         the target publisher
     * @param tracer            the tracer
     * @param operationName     the operation name that should be started
     * @param conversionService the {@code ConversionService} instance
     * @param tracingObserver   the tracing observer
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                String operationName,
                                ConversionService conversionService,
                                TracingObserver<T> tracingObserver) {
        super(publisher, tracer, operationName, conversionService, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments. This constructor
     * will just add tracing of the existing span if it is present.
     *
     * @param publisher         the target publisher
     * @param tracer            the tracer
     * @param conversionService the {@code ConversionService} instance
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                ConversionService conversionService) {
        super(publisher, tracer, conversionService);
    }

    /**
     * Creates a new tracing publisher for the given arguments. This constructor
     * will just add tracing of the existing span if it is present.
     *
     * @param publisher         the target publisher
     * @param tracer            the tracer
     * @param conversionService the {@code ConversionService} instance
     * @param tracingObserver   the tracing observer
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                ConversionService conversionService,
                                TracingObserver<T> tracingObserver) {
        super(publisher, tracer, conversionService, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher         the target publisher
     * @param tracer            the tracer
     * @param spanBuilder       the span builder that represents the span that will be
     * @param conversionService the {@code ConversionService} instance
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                SpanBuilder spanBuilder,
                                ConversionService conversionService) {
        super(publisher, tracer, spanBuilder, conversionService);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param tracer          the tracer
     * @param spanBuilder     the span builder that represents the span that will be
     * @param conversionService the {@code ConversionService} instance
     * @param tracingObserver the tracing observer
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                SpanBuilder spanBuilder,
                                ConversionService conversionService,
                                TracingObserver<T> tracingObserver) {
        super(publisher, tracer, spanBuilder, conversionService, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher         the target publisher
     * @param tracer            the tracer
     * @param spanBuilder       the span builder that represents the span that will
     *                          be created when the publisher is subscribed to
     * @param isSingle          true if the publisher emits a single item
     * @param conversionService the {@code ConversionService} instance
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                SpanBuilder spanBuilder,
                                boolean isSingle,
                                ConversionService conversionService) {
        super(publisher, tracer, spanBuilder, isSingle, conversionService);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param tracer          the tracer
     * @param spanBuilder     the span builder that represents the span that will
     *                        be created when the publisher is subscribed to
     * @param isSingle        true if the publisher emits a single item
     * @param conversionService the {@code ConversionService} instance
     * @param tracingObserver the tracing observer
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                SpanBuilder spanBuilder,
                                boolean isSingle,
                                ConversionService conversionService,
                                TracingObserver<T> tracingObserver) {
        super(publisher, tracer, spanBuilder, isSingle, conversionService, tracingObserver);
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> subscriber) {
        subscribe((Subscriber) subscriber);
    }

    @Override
    protected void doSubscribe(Subscriber<? super T> actual,
                               ScopeManager scopeManager,
                               Span span,
                               boolean finishOnClose) {
        CoreSubscriber<? super T> coreActual = Operators.toCoreSubscriber(actual);
        publisher.subscribe(new TracingCoreSubscriber(scopeManager, span, actual, finishOnClose, coreActual.currentContext()));
    }

    private final class TracingCoreSubscriber extends TracingSubscriber implements CoreSubscriber<T> {

        private final Context context;

        private TracingCoreSubscriber(ScopeManager scopeManager,
                                      Span span,
                                      Subscriber<? super T> actual,
                                      boolean finishOnClose,
                                      Context context) {
            super(scopeManager, span, actual, finishOnClose);
            this.context = context;
        }

        @Override
        public Context currentContext() {
            return context;
        }
    }
}
