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
package io.micronaut.tracing.opentelemetry.instrument.http.client;

import io.micronaut.context.annotation.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the OpenTelemetry HTTP client metrics ({@code http.client.request.duration}).
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@ConfigurationProperties(OpenTelemetryHttpClientMetricsConfig.PREFIX)
public class OpenTelemetryHttpClientMetricsConfig {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.opentelemetry.http.client.metrics";

    private Boolean enabled;

    /**
     * Whether the HTTP client metrics are recorded. When not set, they are recorded only when the
     * OpenTelemetry meter provider exports metrics: an {@code otel.metrics.exporter} other than
     * {@code none}, or a {@code MetricReader} registered on the SDK.
     *
     * @return {@code true} or {@code false} if configured, {@code null} to detect it
     */
    @Nullable
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Whether the HTTP client metrics are recorded. Default value: recorded only if metrics are exported.
     *
     * @param enabled {@code true} to record the metrics, {@code false} to never record them
     */
    public void setEnabled(@Nullable Boolean enabled) {
        this.enabled = enabled;
    }
}
