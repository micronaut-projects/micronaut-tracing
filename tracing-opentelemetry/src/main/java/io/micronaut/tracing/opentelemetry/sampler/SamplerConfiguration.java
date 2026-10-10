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

import io.micronaut.context.annotation.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the sampling applied on top of the sampler configured with {@code otel.traces.sampler}:
 * a rate limit of the new traces and the {@link SamplingRuleConfiguration sampling rules}.
 *
 * @since 8.4.0
 */
@ConfigurationProperties(SamplerConfiguration.PREFIX)
public class SamplerConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.opentelemetry.sampler";

    private RateLimitConfiguration rateLimit = new RateLimitConfiguration();

    /**
     * @return the rate limit configuration
     */
    public RateLimitConfiguration getRateLimit() {
        return rateLimit;
    }

    /**
     * @param rateLimit the rate limit configuration
     */
    public void setRateLimit(RateLimitConfiguration rateLimit) {
        this.rateLimit = rateLimit;
    }

    /**
     * Configuration of the rate limit of the new (root) traces.
     */
    @ConfigurationProperties(RateLimitConfiguration.PREFIX)
    public static class RateLimitConfiguration {

        /**
         * The configuration prefix, relative to {@link SamplerConfiguration#PREFIX}.
         */
        public static final String PREFIX = "rate-limit";

        @Nullable
        private Double tracesPerSecond;
        @Nullable
        private Integer burst;

        /**
         * @return the maximum number of new traces sampled per second, {@code null} for no limit
         */
        @Nullable
        public Double getTracesPerSecond() {
            return tracesPerSecond;
        }

        /**
         * Sets the maximum number of new traces sampled per second, on average. Fractions are allowed, for
         * example {@code 0.5} for one trace every two seconds. Not set by default (no limit).
         *
         * @param tracesPerSecond the maximum number of new traces per second
         */
        public void setTracesPerSecond(@Nullable Double tracesPerSecond) {
            this.tracesPerSecond = tracesPerSecond;
        }

        /**
         * @return the maximum number of new traces sampled in a burst, {@code null} for the default
         */
        @Nullable
        public Integer getBurst() {
            return burst;
        }

        /**
         * Sets the maximum number of new traces sampled at once after an idle period: the capacity of the
         * token bucket. Defaults to one second worth of traces ({@code traces-per-second} rounded up, at
         * least 1).
         *
         * @param burst the burst capacity
         */
        public void setBurst(@Nullable Integer burst) {
            this.burst = burst;
        }

        /**
         * @return whether a rate limit is configured
         */
        public boolean isEnabled() {
            return tracesPerSecond != null;
        }

        /**
         * @return the effective burst capacity
         */
        int effectiveBurst() {
            if (burst != null) {
                return burst;
            }
            return tracesPerSecond == null ? 1 : (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.ceil(tracesPerSecond)));
        }
    }
}
