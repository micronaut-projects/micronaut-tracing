/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.tracing.opentelemetry.instrument.jdbc;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder;

/**
 * The configuration class for jdbc telemetry.
 */
@Requires(property = JdbcTelemetryConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@ConfigurationProperties(JdbcTelemetryConfiguration.PREFIX)
class JdbcTelemetryConfiguration {

    public static final String PREFIX = "otel.instrumentation.jdbc";

    @ConfigurationBuilder(prefixes = "set")
    final JdbcTelemetryBuilder builder;

    private Boolean enabled;

    JdbcTelemetryConfiguration(OpenTelemetry openTelemetry) {
        builder = JdbcTelemetry.builder(openTelemetry);
    }

    /**
     * @return is jdbc telemetry enabled.
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * @param enabled enables the jdbc telemetry.
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

}
