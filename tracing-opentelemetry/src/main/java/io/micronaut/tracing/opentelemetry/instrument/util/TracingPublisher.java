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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;


/**
 * A reactive streams publisher that traces.
 *
 * @param <T> the type of element signaled
 * @param <REQ> the type of request element
 * @author Nemanja Mikic
 * @since 4.1.0
 */
@SuppressWarnings("PublisherImplementation")
public class TracingPublisher<T, REQ> implements Publishers.MicronautPublisher<T> {

    protected final Publisher<T> publisher;
    private final Instrumenter<REQ, Object> instrumenter;
    @Nullable
    private final REQ request;
    private final TracingObserver<T> observer;

    /**
     * @param publisher      the target publisher
     * @param instrumenter   the instrumenter
     * @param request the request object
     * @param observer the tracing observer
     */
    public TracingPublisher(Publisher<T> publisher,
                            Instrumenter<REQ, Object> instrumenter,
                            @Nullable REQ request, TracingObserver<T> observer) {
        this.publisher = publisher;
        this.instrumenter = instrumenter;
        this.request = request;
        this.observer = observer;
    }

    @Override
    public void subscribe(Subscriber<? super T> actual) {
        Context parentContext = Context.current();

        if (instrumenter != null && instrumenter.shouldStart(parentContext, request)) {
            parentContext = instrumenter.start(parentContext, request);
        }

        try (Scope ignored = parentContext.makeCurrent()) {
            doSubscribe(actual, parentContext);
        }
    }

    /**
     * Do subscribe to the publisher.
     *
     * @param actual        The actual subscriber
     * @param context       The context
     */
    @Internal
    protected void doSubscribe(Subscriber<? super T> actual, Context context) {
        publisher.subscribe(new TracingSubscriber(actual, context));
    }


    /**
     * The tracing subscriber.
     */
    @Internal
    protected class TracingSubscriber implements Subscriber<T> {

        final Context context;
        final Subscriber<? super T> actual;

        public TracingSubscriber(Subscriber<? super T> actual, Context context) {
            this.context = context;
            this.actual = actual;
        }

        @Override
        public void onSubscribe(@NotNull Subscription s) {
            try (Scope ignored = context.makeCurrent()) {
                observer.doOnSubscribe(context);
                actual.onSubscribe(s);
            }
        }

        @Override
        public void onNext(T object) {
            try (Scope ignored = context.makeCurrent()) {
                observer.doOnNext(object, context);
                actual.onNext(object);
            } finally {
                if (instrumenter != null) {
                    instrumenter.end(context, request, object, null);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            try (Scope ignored = context.makeCurrent()) {
                observer.doOnError(t, context);
                actual.onError(t);
            } finally {
                if (instrumenter != null) {
                    instrumenter.end(context, request, null, t);
                }
            }
        }

        @Override
        public void onComplete() {
            try (Scope ignored = context.makeCurrent()) {
                actual.onComplete();
                observer.doOnFinish(context);
            } finally {
                if (instrumenter != null) {
                    instrumenter.end(context, request, null, null);
                }
            }
        }
    }

}
