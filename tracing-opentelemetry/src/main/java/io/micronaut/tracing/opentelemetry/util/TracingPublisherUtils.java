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

import io.micronaut.core.annotation.NonNull;
import io.opentelemetry.api.trace.SpanBuilder;
import org.reactivestreams.Publisher;
import reactor.core.CorePublisher;

/**
 * Tracing publisher utils.
 *
 * @author Nemanja Mikic
 */
public final class TracingPublisherUtils {

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param tracingObserver the tracing observer
     * @param <T>             the publisher's type
     * @return new instance
     */
    public static <T> TracingPublisher<T> createTracingPublisher(Publisher<T> publisher,
                                                                 @NonNull TracingObserver<T> tracingObserver) {

        if (publisher instanceof CorePublisher) {
            return new TracingCorePublisher<>((CorePublisher<T>) publisher, tracingObserver);
        }
        return new TracingPublisher<>(publisher, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param spanBuilder     the span builder that represents the span that will
     *                        be created when the publisher is subscribed to
     * @param tracingObserver the tracing observer
     * @param <T>             the publisher's type
     * @return new instance
     */
    public static <T> TracingPublisher<T> createTracingPublisher(Publisher<T> publisher,
                                                                 SpanBuilder spanBuilder,
                                                                 @NonNull TracingObserver<T> tracingObserver) {
        if (publisher instanceof CorePublisher) {
            return new TracingCorePublisher<>((CorePublisher<T>) publisher, spanBuilder, tracingObserver);
        }
        return new TracingPublisher<>(publisher, spanBuilder, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher       the target publisher
     * @param spanBuilder     the span builder that represents the span that will
     *                        be created when the publisher is subscribed to
     * @param isSingle        true if the publisher emits a single item
     * @param tracingObserver the tracing observer
     * @param <T>             the publisher's type
     * @return new instance
     */
    public static <T> TracingPublisher<T> createTracingPublisher(Publisher<T> publisher,
                                                                 SpanBuilder spanBuilder,
                                                                 boolean isSingle,
                                                                 @NonNull TracingObserver<T> tracingObserver) {

        if (publisher instanceof CorePublisher) {
            return new TracingCorePublisher<>((CorePublisher<T>) publisher, spanBuilder, isSingle, tracingObserver);
        }
        return new TracingPublisher<>(publisher, spanBuilder, isSingle, tracingObserver);
    }
}
