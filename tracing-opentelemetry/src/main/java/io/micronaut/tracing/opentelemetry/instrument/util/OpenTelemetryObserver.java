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
 * OpenTelemetry observer.
 *
 * @param <T> The publisher's type
 * @author Nemanja Mikic
 * @since 4.2.0
 */
public interface OpenTelemetryObserver<T> {

    /**
     * No op observer.
     */
    OpenTelemetryObserver<?> NO_OP = new OpenTelemetryObserver<Object>() { };

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
     * @param openTelemetryContext the {@link Context}
     */
    default void doOnError(@NonNull Throwable throwable, @NonNull Context openTelemetryContext) {
        // no-op
    }
}
