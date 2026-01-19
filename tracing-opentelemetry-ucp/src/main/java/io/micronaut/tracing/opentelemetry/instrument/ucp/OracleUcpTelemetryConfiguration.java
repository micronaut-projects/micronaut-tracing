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
package io.micronaut.tracing.opentelemetry.instrument.ucp;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.oracleucp.v11_2.OracleUcpTelemetry;

/**
 * Configuration class for Oracle UCP telemetry.
 *
 * @author Andreas Brenk
 * @since 7.2.1
 */
@Requires(property = OracleUcpTelemetryConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@ConfigurationProperties(OracleUcpTelemetryConfiguration.PREFIX)
class OracleUcpTelemetryConfiguration {

    public static final String PREFIX = "otel.instrumentation.ucp";

    final OracleUcpTelemetry oracleUcpTelemetry;

    private Boolean enabled;

    OracleUcpTelemetryConfiguration(OpenTelemetry openTelemetry) {
        oracleUcpTelemetry = OracleUcpTelemetry.create(openTelemetry);
    }

    /**
     * @return is Oracle UCP telemetry enabled.
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * @param enabled enables Oracle UCP telemetry.
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

}
