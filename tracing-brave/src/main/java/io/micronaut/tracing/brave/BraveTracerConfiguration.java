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
package io.micronaut.tracing.brave;

import brave.Clock;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.sampler.CountingSampler;
import brave.sampler.Sampler;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.tracing.zipkin.http.client.HttpClientSenderConfiguration;
import jakarta.inject.Inject;

import static io.micronaut.context.env.Environment.DEFAULT_NAME;
import static io.micronaut.core.util.StringUtils.TRUE;

/**
 * Configuration properties for Brave.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = Tracing.class)
@Requires(property = BraveTracerConfiguration.PREFIX + ".enabled", value = TRUE)
@ConfigurationProperties(BraveTracerConfiguration.PREFIX)
public class BraveTracerConfiguration implements Toggleable {

    public static final String PREFIX = "tracing.zipkin";
    public static final float DEFAULT_SAMPLER_PROBABILITY = 0.1f;

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    @ConfigurationBuilder(prefixes = "", excludes = {"clock", "endpoint", "spanReporter", "propagationFactory", "currentTraceContext", "sampler"})
    protected Tracing.Builder tracingBuilder = Tracing.newBuilder();

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * Constructs a new {@code BraveTracerConfiguration}.
     *
     * @param configuration the application configuration
     */
    public BraveTracerConfiguration(ApplicationConfiguration configuration) {
        tracingBuilder.sampler(CountingSampler.create(DEFAULT_SAMPLER_PROBABILITY));
        if (configuration != null) {
            tracingBuilder.localServiceName(configuration.getName().orElse(DEFAULT_NAME));
        } else {
            tracingBuilder.localServiceName(DEFAULT_NAME);
        }
    }

    /**
     * @return true if enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled true if tracing is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @param samplerConfiguration the configuration
     */
    @Inject
    public void setSamplerConfiguration(@Nullable SamplerConfiguration samplerConfiguration) {
        if (samplerConfiguration != null) {
            tracingBuilder.sampler(CountingSampler.create(samplerConfiguration.getProbability()));
        }
    }

    /**
     * @return the builder
     */
    public Tracing.Builder getTracingBuilder() {
        return tracingBuilder;
    }

    /**
     * @param sampler the sampler
     */
    @Inject
    public void setSampler(@Nullable Sampler sampler) {
        if (sampler != null) {
            tracingBuilder.sampler(sampler);
        }
    }

    /**
     * @param propagationFactory the factory
     */
    @Inject
    public void setPropagationFactory(@Nullable Propagation.Factory propagationFactory) {
        if (propagationFactory != null) {
            tracingBuilder.propagationFactory(propagationFactory);
        }
    }

    /**
     * @param clock the clock
     */
    @Inject
    public void setClock(@Nullable Clock clock) {
        if (clock != null) {
            tracingBuilder.clock(clock);
        }
    }

    /**
     * Sets the current trace context.
     *
     * @param traceContext the context
     */
    @Inject
    public void setCurrentTraceContext(CurrentTraceContext traceContext) {
        if (traceContext != null) {
            tracingBuilder.currentTraceContext(traceContext);
        }
    }

    /**
     * Used to configure HTTP trace sending under the {@code tracing.zipkin.http} namespace.
     */
    @ConfigurationProperties("http")
    @Requires(property = BraveHttpClientSenderConfiguration.PREFIX)
    @Requires(classes = Tracing.class)
    public static class BraveHttpClientSenderConfiguration extends HttpClientSenderConfiguration {
        public static final String PREFIX = BraveTracerConfiguration.PREFIX + ".http";
    }

    /**
     * The sampler configuration.
     */
    @ConfigurationProperties("sampler")
    @Requires(classes = CountingSampler.class)
    @Requires(missingBeans = Sampler.class)
    public static class SamplerConfiguration {

        private float probability = DEFAULT_SAMPLER_PROBABILITY;

        /**
         * Get sampler probability. A value of 1.0 indicates to sample all requests.
         * A value of 0.1 indicates to sample 10% of requests.
         *
         * @return probability
         */
        public float getProbability() {
            return probability;
        }

        /**
         * Sets the sampler probability used by the default {@code CountingSampler}.
         * A value of 1.0 indicates to sample all requests.
         * A value of 0.1 indicates to sample 10% of requests.
         *
         * @param probability the probability
         */
        public void setProbability(float probability) {
            this.probability = probability;
        }
    }
}
