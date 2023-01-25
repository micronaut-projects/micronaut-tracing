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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import org.reactivestreams.Publisher;
import reactor.core.CorePublisher;

/**
 * Tracing publisher utils.
 *
 * @author Denis Stepanov
 */
public final class TracingPublisherUtils {

    private TracingPublisherUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher         the target publisher
     * @param tracer            the tracer
     * @param conversionService the {@code ConversionService} instance
     * @param tracingObserver   the tracing observer
     * @param <T>               the publisher's type
     * @return new instance
     */
    public static <T> TracingPublisher<T> createTracingPublisher(Publisher<T> publisher,
                                                                 Tracer tracer,
                                                                 ConversionService conversionService,
                                                                 @NonNull TracingObserver<T> tracingObserver) {

        if (publisher instanceof CorePublisher) {
            return new TracingCorePublisher<>((CorePublisher<T>) publisher, tracer, conversionService, tracingObserver);
        }
        return new TracingPublisher<>(publisher, tracer, conversionService, tracingObserver);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param publisher         the target publisher
     * @param tracer            the tracer
     * @param spanBuilder       the span builder that represents the span that will
     *                          be created when the publisher is subscribed to
     * @param conversionService the {@code ConversionService} instance
     * @param tracingObserver   the tracing observer
     * @param <T>               the publisher's type
     * @return new instance
     */
    public static <T> TracingPublisher<T> createTracingPublisher(Publisher<T> publisher,
                                                                 Tracer tracer,
                                                                 SpanBuilder spanBuilder,
                                                                 ConversionService conversionService,
                                                                 @NonNull TracingObserver<T> tracingObserver) {

        if (publisher instanceof CorePublisher) {
            return new TracingCorePublisher<>((CorePublisher<T>) publisher, tracer, spanBuilder, conversionService, tracingObserver);
        }
        return new TracingPublisher<>(publisher, tracer, spanBuilder, conversionService, tracingObserver);
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
     * @param tracingObserver   the tracing observer
     * @param <T>               the publisher's type
     * @return new instance
     */
    public static <T> TracingPublisher<T> createTracingPublisher(Publisher<T> publisher,
                                                                 Tracer tracer,
                                                                 SpanBuilder spanBuilder,
                                                                 boolean isSingle,
                                                                 ConversionService conversionService,
                                                                 @NonNull TracingObserver<T> tracingObserver) {

        if (publisher instanceof CorePublisher) {
            return new TracingCorePublisher<>((CorePublisher<T>) publisher, tracer, spanBuilder, isSingle, conversionService, tracingObserver);
        }
        return new TracingPublisher<>(publisher, tracer, spanBuilder, isSingle, conversionService, tracingObserver);
    }
}
