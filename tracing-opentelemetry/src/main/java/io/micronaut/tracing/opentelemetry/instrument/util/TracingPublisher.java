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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

/**
 * A reactive streams publisher that traces.
 *
 * @param <T> the type of element signaled
 * @author Nemanja Mikic
 * @since 4.1.0
 */
@SuppressWarnings("PublisherImplementation")
public class TracingPublisher<T> implements Publishers.MicronautPublisher<T> {

    private final Publisher<T> publisher;
    private final Instrumenter<ClassAndMethod, Object> instrumenter;
    @Nullable
    private final ClassAndMethod classAndMethod;
    private final Context parentContext;

    /**
     * @param publisher      the target publisher
     * @param instrumenter   the instrumenter
     * @param classAndMethod the operation name that should be started
     * @param parentContext  the parent {@link Context}.
     */
    public TracingPublisher(Publisher<T> publisher,
                            Instrumenter<ClassAndMethod, Object> instrumenter,
                            @Nullable ClassAndMethod classAndMethod,
                            Context parentContext) {
        this.publisher = publisher;
        this.instrumenter = instrumenter;
        this.classAndMethod = classAndMethod;
        this.parentContext = parentContext;
    }

    @Override
    public void subscribe(Subscriber<? super T> actual) {

        if (instrumenter == null || !instrumenter.shouldStart(parentContext, classAndMethod)) {
            publisher.subscribe(new CoreSubscriber<T>() {
                @Override
                public void onSubscribe(@NotNull Subscription s) {
                    doOnSubscribe(parentContext);
                    actual.onSubscribe(s);
                }

                @Override
                public void onNext(T object) {
                    doOnNext(object, parentContext);
                    actual.onNext(object);
                }

                @Override
                public void onError(Throwable t) {
                    doOnError(t, parentContext);
                    actual.onError(t);
                }

                @Override
                public void onComplete() {
                    actual.onComplete();
                    doOnFinish(parentContext);
                }
            });
            return;
        }

        Context context = instrumenter.start(parentContext, classAndMethod);

        try (Scope ignored = context.makeCurrent()) {
            publisher.subscribe(new CoreSubscriber<T>() {
                @Override
                public void onSubscribe(Subscription s) {
                    try (Scope ignored = context.makeCurrent()) {
                        doOnSubscribe(context);
                        actual.onSubscribe(s);
                    }
                }

                @Override
                public void onNext(T object) {
                    try (Scope ignored = context.makeCurrent()) {
                        doOnNext(object, context);
                        actual.onNext(object);
                    } finally {
                        instrumenter.end(context, classAndMethod, object, null);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    try (Scope ignored = context.makeCurrent()) {
                        doOnError(t, context);
                        actual.onError(t);
                    } finally {
                        instrumenter.end(context, classAndMethod, null, t);
                    }
                }

                @Override
                public void onComplete() {
                    try (Scope ignored = context.makeCurrent()) {
                        actual.onComplete();
                        doOnFinish(context);
                    } finally {
                        instrumenter.end(context, classAndMethod, null, null);
                    }
                }
            });
        }
    }

    /**
     * For subclasses to override and implement custom behaviour when an item is emitted.
     *
     * @param object  The object
     * @param context The context
     */
    protected void doOnNext(@NonNull T object, @NonNull Context context) {
        // no-op
    }

    /**
     * For subclasses to override and implement custom on-subscribe behaviour.
     *
     * @param context the context
     */
    protected void doOnSubscribe(@NonNull Context context) {
        // no-op
    }

    /**
     * For subclasses to override and implement custom on-finish behaviour. Fired
     * prior to calling end on the span.
     *
     * @param context the context
     */
    @SuppressWarnings("WeakerAccess")
    protected void doOnFinish(@NonNull Context context) {
        // no-op
    }

    /**
     * For subclasses to override and implement custom on-error behaviour.
     *
     * @param throwable the error
     * @param span      the span
     */
    protected void doOnError(@NonNull Throwable throwable, @NonNull Context span) {
        // no-op
    }
}
