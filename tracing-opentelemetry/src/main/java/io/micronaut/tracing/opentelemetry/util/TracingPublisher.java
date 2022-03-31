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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.tracing.opentelemetry.interceptor.TraceInterceptor;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;

/**
 * A reactive streams publisher that traces.
 *
 * @param <T> the type of element signaled
 * @author Nemanja Mikic
 * @since 1.0
 */
@SuppressWarnings("PublisherImplementation")
public class TracingPublisher<T> implements Publishers.MicronautPublisher<T> {

    protected final Publisher<T> publisher;
    private final Tracer tracer;
    private final SpanBuilder spanBuilder;
    private final Span parentSpan;
    private final boolean isSingle;
    private final TracingObserver tracingObserver;

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher     the target publisher
     * @param tracer        the tracer
     * @param operationName the operation name that should be started
     */
    public TracingPublisher(Publisher<T> publisher,
                            Tracer tracer,
                            String operationName) {
        this(publisher, tracer, tracer.spanBuilder(operationName), TracingObserver.NO_OP);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param tracer          the tracer
     * @param operationName   the operation name that should be started
     * @param tracingObserver the tracing observer
     */
    public TracingPublisher(Publisher<T> publisher,
                            Tracer tracer,
                            String operationName,
                            @NonNull TracingObserver tracingObserver) {
        this(publisher, tracer, tracer.spanBuilder(operationName), tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments. This constructor
     * will just add tracing of the existing span if it is present.
     *
     * @param publisher the target publisher
     * @param tracer    the tracer
     */
    public TracingPublisher(Publisher<T> publisher, Tracer tracer) {
        this(publisher, tracer, (SpanBuilder) null, TracingObserver.NO_OP);
    }

    /**
     * Creates a new tracing publisher for the given arguments. This constructor
     * will just add tracing of the existing span if it is present.
     *
     * @param publisher       the target publisher
     * @param tracer          the tracer
     * @param tracingObserver the tracing observer
     */
    public TracingPublisher(Publisher<T> publisher,
                            Tracer tracer,
                            @NonNull TracingObserver tracingObserver) {
        this(publisher, tracer, (SpanBuilder) null, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher   the target publisher
     * @param tracer      the tracer
     * @param spanBuilder the span builder that represents the span that will be
     */
    public TracingPublisher(Publisher<T> publisher,
                            Tracer tracer,
                            SpanBuilder spanBuilder) {
        this(publisher, tracer, spanBuilder, Publishers.isSingle(publisher.getClass()), TracingObserver.NO_OP);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param tracer          the tracer
     * @param spanBuilder     the span builder that represents the span that will be
     * @param tracingObserver the tracing observer
     */
    public TracingPublisher(Publisher<T> publisher,
                            Tracer tracer,
                            SpanBuilder spanBuilder,
                            @NonNull TracingObserver tracingObserver) {
        this(publisher, tracer, spanBuilder, Publishers.isSingle(publisher.getClass()), tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher   the target publisher
     * @param tracer      the tracer
     * @param spanBuilder the span builder that represents the span that will
     *                    be created when the publisher is subscribed to
     * @param isSingle    true if the publisher emits a single item
     */
    public TracingPublisher(Publisher<T> publisher,
                            Tracer tracer,
                            SpanBuilder spanBuilder,
                            boolean isSingle) {
        this(publisher, tracer, spanBuilder, isSingle, TracingObserver.NO_OP);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param tracer          the tracer
     * @param spanBuilder     the span builder that represents the span that will
     *                        be created when the publisher is subscribed to
     * @param isSingle        true if the publisher emits a single item
     * @param tracingObserver the tracing observer
     */
    public TracingPublisher(Publisher<T> publisher,
                            Tracer tracer,
                            SpanBuilder spanBuilder,
                            boolean isSingle, @NonNull TracingObserver tracingObserver) {
        this.publisher = publisher;
        this.tracer = tracer;
        this.spanBuilder = spanBuilder;
        this.parentSpan = Span.current();
        this.isSingle = isSingle;
        this.tracingObserver = tracingObserver;
        if (parentSpan != null && spanBuilder != null) {
            spanBuilder.setParent(Context.current().with(parentSpan));
        }
    }

    @Override
    public void subscribe(Subscriber<? super T> actual) {
        Span span;
        boolean finishOnClose;
        if (spanBuilder == null) {
            span = parentSpan;
            finishOnClose = isContinued();
        } else {
            span = spanBuilder.startSpan();
            finishOnClose = true;
        }

        if (span == null) {
            publisher.subscribe(actual);
            return;
        }

        try (Scope ignored = Span.current() != span ? span.makeCurrent() : null) {
            doSubscribe(actual, span, finishOnClose);
        }
    }

    /**
     * Do subscribe to the publisher.
     *
     * @param actual        The actual subscriber
     * @param span          The span
     * @param finishOnClose Should finish on close?
     */
    @Internal
    protected void doSubscribe(Subscriber<? super T> actual, Span span, boolean finishOnClose) {
        publisher.subscribe(new TracingSubscriber(span, actual, finishOnClose));
    }

    /**
     * Designed for subclasses to override if the current active span is to be continued by this publisher. False by default.
     * This only has effects if no spanBuilder was defined.
     *
     * @return true, if the current span should be continued by this publisher
     * @since 2.0.3
     */
    protected boolean isContinued() {
        return tracingObserver.isContinued();
    }

    /**
     * Designed for subclasses to override if the span needs to be finished upon error. True by default.
     *
     * @return true, if the active span needs to be finished on error
     * @since 2.0.3
     */
    protected boolean isFinishOnError() {
        return tracingObserver.isFinishOnError();
    }

    /**
     * Designed for subclasses to override and implement custom behaviour when an item is emitted.
     *
     * @param object The object
     * @param span   The span
     */
    protected void doOnNext(@NonNull T object, @NonNull Span span) {
        tracingObserver.doOnNext(object, span);
    }

    /**
     * Designed for subclasses to override and implement custom on subscribe behaviour.
     *
     * @param span The span
     */
    protected void doOnSubscribe(@NonNull Span span) {
        tracingObserver.doOnSubscribe(span);
    }

    /**
     * Designed for subclasses to override and implement custom on finish behaviour. Fired
     * prior to calling {@link Span#end()}.
     *
     * @param span The span
     */
    @SuppressWarnings("WeakerAccess")
    protected void doOnFinish(@NonNull Span span) {
        tracingObserver.doOnFinish(span);
    }

    /**
     * Designed for subclasses to override and implement custom on error behaviour.
     *
     * @param throwable The error
     * @param span      The span
     */
    protected void doOnError(@NonNull Throwable throwable, @NonNull Span span) {
        tracingObserver.doOnError(throwable, span);
    }

    private void onError(Throwable t, Span span) {
        TraceInterceptor.logError(span, t);
        doOnError(t, span);
    }

    /**
     * The tracing subscriber.
     */
    @Internal
    protected class TracingSubscriber implements Subscriber<T> {
        private final Span span;
        private final Subscriber<? super T> actual;
        private final boolean finishOnClose;
        private boolean finished;

        public TracingSubscriber(Span span, Subscriber<? super T> actual, boolean finishOnClose) {
            this.span = span;
            this.actual = actual;
            this.finishOnClose = finishOnClose;
            finished = false;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (Span.current() != span) {
                try (Scope ignored = span.makeCurrent()) {
                    TracingPublisher.this.doOnSubscribe(span);
                    actual.onSubscribe(s);
                }
            } else {
                TracingPublisher.this.doOnSubscribe(span);
                actual.onSubscribe(s);
            }
        }

        @Override
        public void onNext(T object) {
            boolean finishAfterNext = isSingle && finishOnClose;
            try (Scope ignored = Span.current() != span ? span.makeCurrent() : null) {
                if (object instanceof MutableHttpResponse) {
                    MutableHttpResponse<?> response = (MutableHttpResponse<?>) object;
                    Optional<?> body = response.getBody();
                    if (body.isPresent()) {
                        Object o = body.get();
                        if (Publishers.isConvertibleToPublisher(o)) {
                            Class<?> type = o.getClass();
                            Publisher<?> resultPublisher = Publishers.convertPublisher(o, Publisher.class);
                            Publisher<?> scopedPublisher = new ScopePropagationPublisher(resultPublisher, tracer, span);
                            response.body(Publishers.convertPublisher(scopedPublisher, type));
                        }
                    }

                }
                TracingPublisher.this.doOnNext(object, span);
                actual.onNext(object);
                if (isSingle) {
                    finished = true;
                    TracingPublisher.this.doOnFinish(span);
                }
            } finally {
                if (finishAfterNext) {
                    span.end();
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            try (Scope ignored = Span.current() != span ? span.makeCurrent() : null) {
                TracingPublisher.this.onError(t, span);
                actual.onError(t);
                finished = true;
            } finally {
                if (finishOnClose && isFinishOnError()) {
                    span.end();
                }
            }
        }

        @Override
        public void onComplete() {
            try (Scope ignored = Span.current() != span ? span.makeCurrent() : null) {
                actual.onComplete();
                TracingPublisher.this.doOnFinish(span);
            } finally {
                if (!finished && finishOnClose) {
                    span.end();
                }
            }
        }
    }
}
