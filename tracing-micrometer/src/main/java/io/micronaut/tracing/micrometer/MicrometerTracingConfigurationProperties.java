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
package io.micronaut.tracing.micrometer;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;

import java.util.Collections;
import java.util.List;

/**
 * Configuration properties for Micrometer Tracing.
 *
 * @author original authors
 * @since 8.0.0
 */
@Requires(property = MicrometerTracingConfigurationProperties.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@ConfigurationProperties(MicrometerTracingConfigurationProperties.PREFIX)
public class MicrometerTracingConfigurationProperties implements Toggleable {

    /**
     * Configuration prefix for Micrometer Tracing.
     */
    public static final String PREFIX = "tracing.micrometer";

    /**
     * The default enabled value.
     */
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;
    private Baggage baggage = new Baggage();

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables Micrometer Tracing bridge beans.
     *
     * @param enabled True if enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Baggage configuration.
     *
     * @return baggage configuration
     */
    public Baggage getBaggage() {
        return baggage;
    }

    /**
     * Sets baggage configuration.
     *
     * @param baggage baggage configuration
     */
    public void setBaggage(Baggage baggage) {
        this.baggage = baggage == null ? new Baggage() : baggage;
    }

    /**
     * Baggage configuration.
     */
    @ConfigurationProperties("baggage")
    public static class Baggage {

        private List<String> remoteFields = Collections.emptyList();
        private List<String> correlationFields = Collections.emptyList();

        /**
         * Baggage field names propagated to remote services.
         *
         * @return baggage field names propagated to remote services
         */
        public List<String> getRemoteFields() {
            return remoteFields;
        }

        /**
         * Sets baggage field names propagated to remote services.
         *
         * @param remoteFields baggage field names propagated to remote services
         */
        public void setRemoteFields(List<String> remoteFields) {
            this.remoteFields = remoteFields == null ? Collections.emptyList() : remoteFields;
        }

        /**
         * Baggage field names correlated locally, for example with logging.
         *
         * @return baggage field names correlated locally, for example with logging
         */
        public List<String> getCorrelationFields() {
            return correlationFields;
        }

        /**
         * Sets baggage field names correlated locally, for example with logging.
         *
         * @param correlationFields baggage field names correlated locally, for example with logging
         */
        public void setCorrelationFields(List<String> correlationFields) {
            this.correlationFields = correlationFields == null ? Collections.emptyList() : correlationFields;
        }
    }
}
