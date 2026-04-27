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
package io.micronaut.tracing.micrometer.brave;

import brave.Tracing;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.tracing.micrometer.MicrometerTracingConfigurationProperties;
import jakarta.inject.Singleton;

/**
 * Creates Micrometer Tracing bridge beans backed by Brave.
 *
 * @author original authors
 * @since 8.0.0
 */
@Factory
@Requires(beans = {MicrometerTracingConfigurationProperties.class, Tracing.class})
public class MicrometerBraveTracingFactory {

    /**
     * Creates a Micrometer current trace context.
     *
     * @param currentTraceContext Brave current trace context
     * @return Micrometer current trace context
     */
    @Singleton
    @Requires(missingBeans = CurrentTraceContext.class)
    BraveCurrentTraceContext currentTraceContext(brave.propagation.CurrentTraceContext currentTraceContext) {
        return new BraveCurrentTraceContext(currentTraceContext);
    }

    /**
     * Creates a Micrometer baggage manager.
     *
     * @param configuration Micrometer tracing configuration
     * @return Micrometer baggage manager
     */
    @Bean(preDestroy = "close")
    @Singleton
    @Primary
    @Requires(missingBeans = BraveBaggageManager.class)
    BraveBaggageManager baggageManager(MicrometerTracingConfigurationProperties configuration) {
        MicrometerTracingConfigurationProperties.Baggage baggage = configuration.getBaggage();
        return new BraveBaggageManager(baggage.getRemoteFields(), baggage.getCorrelationFields());
    }

    /**
     * Creates a Micrometer tracer backed by Brave.
     *
     * @param tracing Brave tracing instance
     * @param currentTraceContext Micrometer current trace context
     * @param baggageManager Micrometer baggage manager
     * @return Micrometer tracer
     */
    @Singleton
    @Requires(missingBeans = io.micrometer.tracing.Tracer.class)
    io.micrometer.tracing.Tracer micrometerTracer(Tracing tracing,
                                                  CurrentTraceContext currentTraceContext,
                                                  BraveBaggageManager baggageManager) {
        return new BraveTracer(tracing.tracer(), currentTraceContext, baggageManager);
    }

    /**
     * Creates a Micrometer propagator backed by Brave.
     *
     * @param tracing Brave tracing instance
     * @return Micrometer propagator
     */
    @Singleton
    @Requires(missingBeans = Propagator.class)
    Propagator propagator(Tracing tracing) {
        return new BravePropagator(tracing);
    }
}
