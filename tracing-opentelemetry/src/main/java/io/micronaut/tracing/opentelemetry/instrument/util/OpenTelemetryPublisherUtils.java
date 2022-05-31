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

import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.reactivestreams.Publisher;
import reactor.core.CorePublisher;

/**
 * The tracing observer.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
public final class OpenTelemetryPublisherUtils {

    private OpenTelemetryPublisherUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param <T> The publisher's type
     * @param <R> the type of request element
     * @param publisher the target publisher
     * @param instrumenter the instrumenter
     * @param request the request object
     * @param context the Open Telemetry context
     * @param observer the tracing observer
     * @return new instance
     */
    public static <T, R> OpenTelemetryPublisher<T, R> createOpenTelemetryPublisher(Publisher<T> publisher,
                                                                                   Instrumenter<R, Object> instrumenter,
                                                                                   Context context,
                                                                                   @Nullable R request, OpenTelemetryObserver<T> observer) {
        if (publisher instanceof CorePublisher) {
            return new OpenTelemetryCorePublisher<>(publisher, instrumenter, context, request, observer);
        }
        return new OpenTelemetryPublisher<>(publisher, instrumenter, context, request, observer);
    }

    /**
     * Creates a new tracing publisher for the given arguments.
     *
     * @param <T> The publisher's type
     * @param <R> the type of request element
     * @param publisher      the target publisher
     * @param instrumenter   the instrumenter
     * @param context the Open Telemetry context
     * @param request the request object
     * @return new instance
     */
    public static <T, R> OpenTelemetryPublisher<T, R> createOpenTelemetryPublisher(Publisher<T> publisher,
                                                                                   Instrumenter<R, Object> instrumenter,
                                                                                   Context context,
                                                                                   @Nullable R request) {
        if (publisher instanceof CorePublisher) {
            return new OpenTelemetryCorePublisher<>(publisher, instrumenter, context, request, (OpenTelemetryObserver<T>) OpenTelemetryObserver.NO_OP);
        }
        return new OpenTelemetryPublisher<>(publisher, instrumenter, context, request, (OpenTelemetryObserver<T>) OpenTelemetryObserver.NO_OP);
    }

}
