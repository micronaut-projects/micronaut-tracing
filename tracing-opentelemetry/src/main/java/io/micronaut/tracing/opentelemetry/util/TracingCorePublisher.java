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
package io.micronaut.tracing.opentelemetry.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.reactivestreams.Subscriber;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * The tracing publisher that supports Reactor's context.
 *
 * @param <T> the type of element signaled
 * @author Nemanja Mikic
 */
@SuppressWarnings("PublisherImplementation")
public class TracingCorePublisher<T> extends TracingPublisher<T> implements CorePublisher<T> {

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher     the target publisher
     * @param tracer        the tracer
     * @param operationName the operation name that should be started
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                String operationName) {
        super(publisher, tracer, operationName);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param tracer          the tracer
     * @param operationName   the operation name that should be started
     * @param tracingObserver the tracing observer
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                Tracer tracer,
                                String operationName,
                                TracingObserver tracingObserver) {
        super(publisher, tracer, operationName, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments. This constructor
     * will just add tracing of the existing span if it is present.
     *
     * @param publisher the target publisher
     */
    public TracingCorePublisher(CorePublisher<T> publisher) {
        super(publisher);
    }

    /**
     * Creates a new tracing publisher for the given arguments. This constructor
     * will just add tracing of the existing span if it is present.
     *
     * @param publisher       the target publisher
     * @param tracingObserver the tracing observer
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                TracingObserver tracingObserver) {
        super(publisher, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher   the target publisher
     * @param spanBuilder the span builder that represents the span that will be
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                SpanBuilder spanBuilder) {
        super(publisher, spanBuilder);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param spanBuilder     the span builder that represents the span that will be
     * @param tracingObserver the tracing observer
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                SpanBuilder spanBuilder,
                                TracingObserver tracingObserver) {
        super(publisher, spanBuilder, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher   the target publisher
     * @param spanBuilder the span builder that represents the span that will
     *                    be created when the publisher is subscribed to
     * @param isSingle    true if the publisher emits a single item
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                SpanBuilder spanBuilder,
                                boolean isSingle) {
        super(publisher, spanBuilder, isSingle);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param spanBuilder     the span builder that represents the span that will
     *                        be created when the publisher is subscribed to
     * @param isSingle        true if the publisher emits a single item
     * @param tracingObserver the tracing observer
     */
    public TracingCorePublisher(CorePublisher<T> publisher,
                                SpanBuilder spanBuilder,
                                boolean isSingle,
                                TracingObserver tracingObserver) {
        super(publisher, spanBuilder, isSingle, tracingObserver);
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> subscriber) {
        subscribe((Subscriber) subscriber);
    }

    @Override
    protected void doSubscribe(Subscriber<? super T> actual,
                               Span span,
                               boolean finishOnClose) {
        CoreSubscriber<? super T> coreActual = Operators.toCoreSubscriber(actual);
        publisher.subscribe(new TracingCoreSubscriber(span, actual, finishOnClose, coreActual.currentContext()));
    }

    private final class TracingCoreSubscriber extends TracingSubscriber implements CoreSubscriber<T> {

        private final Context context;

        public TracingCoreSubscriber(Span span,
                                     Subscriber<? super T> actual,
                                     boolean finishOnClose,
                                     Context context) {
            super(span, actual, finishOnClose);
            this.context = context;
        }

        @Override
        public Context currentContext() {
            return context;
        }
    }
}
