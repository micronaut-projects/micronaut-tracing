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
package io.micronaut.tracing.opentelemetry.instrument.mongodb;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetryBuilder;

/**
 * Configuration of the OpenTelemetry tracing of MongoDB commands sent through the MongoDB Java driver.
 *
 * <p>The OpenTelemetry {@link MongoTelemetryBuilder} settings are exposed under the same prefix:
 * {@code query-sanitization-enabled} (default {@code true}) replaces the values of the command
 * recorded in {@code db.statement} ({@code db.query.text}) with {@code ?}, and
 * {@code max-normalized-query-length} (default {@code 32768}) limits the length of the recorded
 * command.</p>
 *
 * @since 8.4.0
 */
@ConfigurationProperties(MongoTelemetryConfiguration.PREFIX)
public final class MongoTelemetryConfiguration implements Toggleable {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "otel.instrumentation.mongo";

    /**
     * The default enable value.
     */
    public static final boolean DEFAULT_ENABLED = true;

    @ConfigurationBuilder(prefixes = "set")
    final MongoTelemetryBuilder builder;

    private boolean enabled = DEFAULT_ENABLED;

    MongoTelemetryConfiguration(OpenTelemetry openTelemetry) {
        builder = MongoTelemetry.builder(openTelemetry);
    }

    /**
     * @return Whether the MongoDB telemetry is enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables the tracing of MongoDB commands. Default value: {@value #DEFAULT_ENABLED}.
     *
     * @param enabled Whether the MongoDB telemetry is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return a new {@link MongoTelemetry} built from this configuration
     */
    MongoTelemetry build() {
        return builder.build();
    }
}
