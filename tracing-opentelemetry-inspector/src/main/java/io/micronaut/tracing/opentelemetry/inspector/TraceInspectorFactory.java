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
package io.micronaut.tracing.opentelemetry.inspector;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Registers the span processor of the trace inspector with the OpenTelemetry SDK built by
 * {@code DefaultOpenTelemetryFactory}, through an {@link OpenTelemetryBuilderCustomizer}.
 *
 * @since 8.4.0
 */
@Factory
@Internal
final class TraceInspectorFactory {

    /**
     * Adds the span processor of the trace inspector to the tracer provider.
     *
     * @param inspector the trace inspector
     * @return the customizer
     */
    @Singleton
    @Named("traceInspector")
    OpenTelemetryBuilderCustomizer traceInspectorCustomizer(DefaultTraceInspector inspector) {
        return builder -> builder.addTracerProviderCustomizer((tracerProviderBuilder, ignored) ->
            tracerProviderBuilder.addSpanProcessor(new TraceInspectorSpanProcessor(inspector)));
    }
}
