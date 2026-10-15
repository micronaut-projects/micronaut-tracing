/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.tracing.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * The publisher returned by a {@code NewSpan} method, in the context of the span of the method.
 *
 * <p>It is usually subscribed after the method returned, by the caller (e.g. with {@code Mono.toFuture()}
 * or by the HTTP server), with the context of the caller current. Its work is often started lazily, on
 * subscription (e.g. an HTTP client call in a {@code flatMap}), so it is subscribed with the propagated
 * context of the method current and in its Reactor context, which makes the span of the method the parent of
 * that work instead of the span of the caller. The signals are emitted to the subscriber in the propagated
 * context of the caller, so the code consuming them does not run with the span of the method current.</p>
 *
 * <p>Requires Reactor and {@code micronaut-core-reactive} on the classpath, which the tracing modules using it
 * provide.</p>
 *
 * @since 8.4.0
 */
@Internal
public final class PropagatedContextPublisher implements Publisher<Object> {

    private final Publisher<?> source;
    private final PropagatedContext propagatedContext;

    private PropagatedContextPublisher(Publisher<?> source, PropagatedContext propagatedContext) {
        this.source = source;
        this.propagatedContext = propagatedContext;
    }

    /**
     * Wraps the publisher returned by a {@code NewSpan} method so that it is subscribed in the propagated
     * context of the method and emits its signals in the propagated context of the caller.
     *
     * @param source            The publisher returned by the method
     * @param propagatedContext The propagated context of the method, with its span
     * @return The publisher subscribed in the propagated context of the method
     */
    public static Publisher<Object> of(Publisher<?> source, PropagatedContext propagatedContext) {
        return new PropagatedContextPublisher(source, propagatedContext);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void subscribe(Subscriber<? super Object> subscriber) {
        CoreSubscriber<? super Object> actual = Operators.toCoreSubscriber(subscriber);
        Subscriber<Object> inContext = new CallerContextSubscriber(actual,
            ReactorPropagation.addPropagatedContext(actual.currentContext(), propagatedContext),
            PropagatedContext.getOrEmpty());
        propagatedContext.propagate(() -> ((Publisher<Object>) source).subscribe(inContext));
    }

    /**
     * Emits the signals of the publisher of a {@code NewSpan} method to its subscriber in the propagated context
     * of the caller, and exposes the propagated context of the method to the publisher.
     *
     * @param actual        The subscriber
     * @param context       The Reactor context of the publisher, with the propagated context of the method
     * @param callerContext The propagated context of the caller, current when it subscribed
     */
    private record CallerContextSubscriber(CoreSubscriber<? super Object> actual,
                                           Context context,
                                           PropagatedContext callerContext) implements CoreSubscriber<Object> {

        @Override
        public Context currentContext() {
            return context;
        }

        // Each signal checks first and only allocates the propagating lambda when the caller context
        // has to be restored, so the usual case (already in the caller context) allocates nothing.

        @Override
        public void onSubscribe(Subscription subscription) {
            if (inCallerContext()) {
                actual.onSubscribe(subscription);
            } else {
                callerContext.propagate(() -> actual.onSubscribe(subscription));
            }
        }

        @Override
        public void onNext(Object item) {
            if (inCallerContext()) {
                actual.onNext(item);
            } else {
                callerContext.propagate(() -> actual.onNext(item));
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (inCallerContext()) {
                actual.onError(throwable);
            } else {
                callerContext.propagate(() -> actual.onError(throwable));
            }
        }

        @Override
        public void onComplete() {
            if (inCallerContext()) {
                actual.onComplete();
            } else {
                callerContext.propagate(actual::onComplete);
            }
        }

        /**
         * @return whether the signal already runs in the caller context: it is bound, or both it and the
         * current context are empty
         */
        private boolean inCallerContext() {
            return callerContext.isBound() || callerContext.isEmpty() && !PropagatedContext.exists();
        }
    }
}
