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
package io.micronaut.tracing.opentelemetry.instrument.redis;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetryBuilder;

/**
 * Configuration of the OpenTelemetry tracing of Redis commands sent through Lettuce.
 *
 * <p>The OpenTelemetry {@link LettuceTelemetryBuilder} settings are exposed under the same prefix:
 * {@code query-sanitization-enabled} (default {@code true}) masks the command arguments recorded in
 * {@code db.query.text}, and {@code encoding-span-events-enabled} (default {@code false}) adds the
 * {@code redis.encode.start} and {@code redis.encode.end} span events.</p>
 *
 * @since 8.4.0
 */
@ConfigurationProperties(LettuceTelemetryConfiguration.PREFIX)
public final class LettuceTelemetryConfiguration implements Toggleable {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "otel.instrumentation.lettuce";

    /**
     * The default enable value.
     */
    public static final boolean DEFAULT_ENABLED = true;

    @ConfigurationBuilder(prefixes = "set")
    final LettuceTelemetryBuilder builder;

    private boolean enabled = DEFAULT_ENABLED;

    LettuceTelemetryConfiguration(OpenTelemetry openTelemetry) {
        builder = LettuceTelemetry.builder(openTelemetry);
    }

    /**
     * @return Whether the Lettuce telemetry is enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables the tracing of Redis commands sent through Lettuce. Default value: {@value #DEFAULT_ENABLED}.
     *
     * @param enabled Whether the Lettuce telemetry is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return a new {@link LettuceTelemetry} built from this configuration
     */
    LettuceTelemetry build() {
        return builder.build();
    }
}
