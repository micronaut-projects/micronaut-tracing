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
import io.opentelemetry.api.trace.Span;

/**
 * The tracing observer.
 *
 * @param <T> The publisher's type
 * @author Nemanja Mikic
 */
public interface TracingObserver<T> {

    /**
     * No op observer.
     */
    TracingObserver<?> NO_OP = new TracingObserver<Object>() { };

    /**
     * Designed for subclasses to override if the current active span is to be
     * continued by this publisher. False by default. This only has effects if
     * no spanBuilder was defined.
     *
     * @return true, if the current span should be continued by this publisher
     * @since 4.0.2
     */
    default boolean isContinued() {
        return false;
    }

    /**
     * Designed for subclasses to override if the span needs to be finished
     * upon error. True by default.
     *
     * @return true, if the active span needs to be finished on error
     * @since 4.0.2
     */
    default boolean isFinishOnError() {
        return true;
    }

    /**
     * Designed for subclasses to override and implement custom behaviour when
     * an item is emitted.
     *
     * @param object The object
     * @param span   The span
     */
    default void doOnNext(@NonNull T object, @NonNull Span span) {
        // no-op
    }

    /**
     * Designed for subclasses to override and implement custom on subscribe behaviour.
     *
     * @param span The span
     */
    default void doOnSubscribe(@NonNull Span span) {
        // no-op
    }

    /**
     * Designed for subclasses to override and implement custom on finish
     * behaviour. Fired prior to calling {@link Span#end()}.
     *
     * @param span The span
     */
    @SuppressWarnings("WeakerAccess")
    default void doOnFinish(@NonNull Span span) {
        // no-op
    }

    /**
     * Designed for subclasses to override and implement custom on error behaviour.
     *
     * @param throwable The error
     * @param span      The span
     */
    default void doOnError(@NonNull Throwable throwable, @NonNull Span span) {
        // no-op
    }
}
