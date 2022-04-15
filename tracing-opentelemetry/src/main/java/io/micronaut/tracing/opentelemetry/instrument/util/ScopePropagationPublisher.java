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

import io.micronaut.core.async.publisher.Publishers;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

/**
 * A <code>Publisher</code> that just propagates tracing state without creating a new span.
 *
 * @param <T> the publisher generic type
 * @author Nemanja Mikic
 */
@SuppressWarnings("PublisherImplementation")
public class ScopePropagationPublisher<T> implements Publishers.MicronautPublisher<T> {

    private final Publisher<T> publisher;
    private final Context parentContext;

    /**
     * Default constructor.
     *
     * @param publisher     the publisher
     * @param parentContext the parent context
     */
    public ScopePropagationPublisher(Publisher<T> publisher,
                                     Context parentContext) {
        this.publisher = publisher;
        this.parentContext = parentContext;
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public void subscribe(Subscriber<? super T> actual) {
        Context context = parentContext;
        if (context == null) {
            publisher.subscribe(actual);
            return;
        }

        try (Scope ignored = context.makeCurrent()) {
            publisher.subscribe(new CoreSubscriber<T>() {
                @Override
                public void onSubscribe(Subscription s) {
                    try (Scope ignored = context.makeCurrent()) {
                        actual.onSubscribe(s);
                    }
                }

                @Override
                public void onNext(T object) {
                    try (Scope ignored = context.makeCurrent()) {
                        actual.onNext(object);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    try (Scope ignored = context.makeCurrent()) {
                        actual.onError(t);
                    }
                }

                @Override
                public void onComplete() {
                    try (Scope ignored = context.makeCurrent()) {
                        actual.onComplete();
                    }
                }
            });
        }
    }
}
