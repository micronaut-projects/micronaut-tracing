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
package io.micronaut.tracing.opentelemetry.instrument.jms;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

/**
 * Configuration of the OpenTelemetry tracing of the messages sent and received through Micronaut JMS.
 *
 * @since 8.4.0
 */
@ConfigurationProperties(JmsTelemetryConfiguration.PREFIX)
public final class JmsTelemetryConfiguration implements Toggleable {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "otel.instrumentation.jms";

    /**
     * The default enable value.
     */
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return Whether the JMS telemetry is enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables the tracing of the messages sent and received through Micronaut JMS. Default value: {@value #DEFAULT_ENABLED}.
     *
     * @param enabled Whether the JMS telemetry is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
