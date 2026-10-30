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
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates Micrometer Tracing bridge beans backed by Brave.
 *
 * @author Nemanja Mikic
 * @since 8.4.0
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
     * Creates the Brave propagation factory that registers the configured baggage fields.
     * Remote fields are propagated to remote services alongside the B3 headers, while
     * correlation fields are kept local to the process. If no baggage fields are configured,
     * Brave's default B3 propagation is returned unchanged.
     *
     * @param configuration Micrometer tracing configuration
     * @return Brave propagation factory
     */
    @Singleton
    @Requires(missingBeans = Propagation.Factory.class)
    Propagation.Factory propagationFactory(MicrometerTracingConfigurationProperties configuration) {
        MicrometerTracingConfigurationProperties.Baggage baggage = configuration.getBaggage();
        List<String> remoteFields = baggage.getRemoteFields();
        List<String> correlationFields = baggage.getCorrelationFields();
        if (remoteFields.isEmpty() && correlationFields.isEmpty()) {
            return B3Propagation.FACTORY;
        }
        BaggagePropagation.FactoryBuilder builder = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY);
        Set<String> registered = new HashSet<>();
        for (String name : remoteFields) {
            if (registered.add(name)) {
                builder.add(BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create(name)));
            }
        }
        for (String name : correlationFields) {
            if (registered.add(name)) {
                builder.add(BaggagePropagationConfig.SingleBaggageField.local(BaggageField.create(name)));
            }
        }
        return builder.build();
    }

    /**
     * Creates a Micrometer baggage manager. Correlation fields are added as span tags.
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
        // BraveBaggageManager(tagFields, remoteFields)
        return new BraveBaggageManager(baggage.getCorrelationFields(), baggage.getRemoteFields());
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
