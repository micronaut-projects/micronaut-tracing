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
package io.micronaut.tracing.opentelemetry.sampler;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Wraps the sampler configured with {@code otel.traces.sampler} in a {@link RootSampler} when a rate limit
 * or sampling rules are configured under {@code tracing.opentelemetry.sampler}.
 * <p>
 * A {@link Sampler} bean of the application replaces the configured sampler entirely (see
 * {@code DefaultOpenTelemetryFactory}), so the rate limit and the rules are then ignored and a warning is
 * logged.
 *
 * @since 8.4.0
 */
@Internal
@Singleton
final class SamplerBuilderCustomizer implements OpenTelemetryBuilderCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(SamplerBuilderCustomizer.class);

    private final SamplerConfiguration configuration;
    private final List<SamplingRuleConfiguration> rules;
    private final BeanContext beanContext;

    SamplerBuilderCustomizer(SamplerConfiguration configuration,
                             List<SamplingRuleConfiguration> rules,
                             BeanContext beanContext) {
        this.configuration = configuration;
        this.rules = rules;
        this.beanContext = beanContext;
    }

    @Override
    public void configure(AutoConfiguredOpenTelemetrySdkBuilder builder) {
        if (!configuration.getRateLimit().isEnabled() && rules.isEmpty()) {
            return;
        }
        if (beanContext.containsBean(Sampler.class)) {
            LOG.warn("A Sampler bean is defined: {}.rate-limit and {}.rules are ignored",
                SamplerConfiguration.PREFIX, SamplerConfiguration.PREFIX);
            return;
        }
        // validate eagerly, so an invalid configuration fails at startup rather than on the first span
        RootSampler sampler = new RootSampler(Sampler.alwaysOn(), rules, configuration.getRateLimit(), Clock.getDefault());
        LOG.debug("Configured sampling: {}", sampler);
        builder.addSamplerCustomizer((configured, config) ->
            new RootSampler(configured, rules, configuration.getRateLimit(), Clock.getDefault()));
    }
}
