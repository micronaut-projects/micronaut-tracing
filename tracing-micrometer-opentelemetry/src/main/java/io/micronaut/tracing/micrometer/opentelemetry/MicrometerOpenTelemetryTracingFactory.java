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
package io.micronaut.tracing.micrometer.opentelemetry;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.tracing.micrometer.MicrometerTracingConfigurationProperties;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.inject.Singleton;

/**
 * Creates Micrometer Tracing bridge beans backed by OpenTelemetry.
 *
 * @author original authors
 * @since 8.0.0
 */
@Factory
@Requires(beans = MicrometerTracingConfigurationProperties.class)
public class MicrometerOpenTelemetryTracingFactory {

    /**
     * Constructs Micrometer OpenTelemetry tracing factory.
     */
    public MicrometerOpenTelemetryTracingFactory() {
    }

    /**
     * Creates a Micrometer current trace context.
     *
     * @return Micrometer current trace context
     */
    @Singleton
    @Requires(missingBeans = CurrentTraceContext.class)
    OtelCurrentTraceContext currentTraceContext() {
        return new OtelCurrentTraceContext();
    }

    /**
     * Creates a Micrometer baggage manager.
     *
     * @param configuration Micrometer tracing configuration
     * @param currentTraceContext Micrometer current trace context
     * @return Micrometer baggage manager
     */
    @Singleton
    @Primary
    @Requires(missingBeans = OtelBaggageManager.class)
    OtelBaggageManager baggageManager(MicrometerTracingConfigurationProperties configuration,
                                      OtelCurrentTraceContext currentTraceContext) {
        MicrometerTracingConfigurationProperties.Baggage baggage = configuration.getBaggage();
        return new OtelBaggageManager(currentTraceContext, baggage.getRemoteFields(), baggage.getCorrelationFields());
    }

    /**
     * Creates a Micrometer tracer backed by OpenTelemetry.
     *
     * @param tracer OpenTelemetry tracer
     * @param currentTraceContext Micrometer current trace context
     * @param baggageManager Micrometer baggage manager
     * @return Micrometer tracer
     */
    @Singleton
    @Requires(beans = io.opentelemetry.api.trace.Tracer.class)
    @Requires(missingBeans = io.micrometer.tracing.Tracer.class)
    io.micrometer.tracing.Tracer micrometerTracer(io.opentelemetry.api.trace.Tracer tracer,
                                                  OtelCurrentTraceContext currentTraceContext,
                                                  OtelBaggageManager baggageManager) {
        return new OtelTracer(tracer, currentTraceContext, event -> { }, baggageManager);
    }

    /**
     * Creates a Micrometer propagator backed by OpenTelemetry propagators.
     *
     * @param openTelemetry OpenTelemetry instance
     * @param tracer OpenTelemetry tracer
     * @return Micrometer propagator
     */
    @Singleton
    @Requires(beans = {OpenTelemetry.class, io.opentelemetry.api.trace.Tracer.class})
    @Requires(missingBeans = Propagator.class)
    Propagator propagator(OpenTelemetry openTelemetry, io.opentelemetry.api.trace.Tracer tracer) {
        return new OtelPropagator(openTelemetry.getPropagators(), tracer);
    }
}
