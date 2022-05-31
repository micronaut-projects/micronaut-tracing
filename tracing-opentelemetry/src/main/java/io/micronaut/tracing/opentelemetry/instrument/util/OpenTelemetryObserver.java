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
import io.opentelemetry.context.Context;

/**
 * The tracing observer.
 *
 * @param <T> The publisher's type
 * @author Nemanja Mikic
 * @since 4.1.0
 */
public interface OpenTelemetryObserver<T> {

    /**
     * No op observer.
     */
    OpenTelemetryObserver<?> NO_OP = new OpenTelemetryObserver<Object>() { };

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
     * For subclasses to override and implement custom behaviour when an item is emitted.
     *
     * @param object  The object
     * @param context The context
     */
    default void doOnNext(@NonNull T object, @NonNull Context context) {
        // no-op
    }

    /**
     * For subclasses to override and implement custom on-subscribe behaviour.
     *
     * @param context the context
     */
    default void doOnSubscribe(@NonNull Context context) {
        // no-op
    }

    /**
     * For subclasses to override and implement custom on-finish behaviour. Fired
     * prior to calling end on the span.
     *
     * @param context the context
     */
    @SuppressWarnings("WeakerAccess")
    default void doOnFinish(@NonNull Context context) {
        // no-op
    }

    /**
     * For subclasses to override and implement custom on-error behaviour.
     *
     * @param throwable the error
     * @param span      the span
     */
    default void doOnError(@NonNull Throwable throwable, @NonNull Context span) {
        // no-op
    }
}
