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
package io.micronaut.tracing.opentelemetry.instrument.http.server;

import io.micronaut.context.annotation.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Configuration of the requests traced by the HTTP server filter.
 *
 * @since 8.4.0
 */
@ConfigurationProperties(OpenTelemetryHttpServerTracingConfig.PREFIX)
public class OpenTelemetryHttpServerTracingConfig {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.opentelemetry.http.server";

    /**
     * Whether the requests to the management endpoints are excluded by default.
     */
    public static final boolean DEFAULT_EXCLUDE_MANAGEMENT_ENDPOINTS = true;

    private boolean excludeManagementEndpoints = DEFAULT_EXCLUDE_MANAGEMENT_ENDPOINTS;
    private List<String> tracedManagementEndpoints = Collections.emptyList();

    /**
     * @return whether the requests to the management endpoints are excluded from tracing
     */
    public boolean isExcludeManagementEndpoints() {
        return excludeManagementEndpoints;
    }

    /**
     * Sets whether the requests to the Micronaut management endpoints ({@code /health}, {@code /info},
     * {@code /prometheus}...) are excluded from tracing, as the paths of {@code otel.exclusions}. Defaults to
     * {@code true}.
     *
     * @param excludeManagementEndpoints whether the management endpoints are excluded
     */
    public void setExcludeManagementEndpoints(boolean excludeManagementEndpoints) {
        this.excludeManagementEndpoints = excludeManagementEndpoints;
    }

    /**
     * @return the ids of the management endpoints that are still traced
     */
    public List<String> getTracedManagementEndpoints() {
        return tracedManagementEndpoints;
    }

    /**
     * Sets the ids of the management endpoints that are still traced when the management endpoints are
     * excluded, for example {@code health}.
     *
     * @param tracedManagementEndpoints the ids of the traced management endpoints
     */
    public void setTracedManagementEndpoints(@Nullable List<String> tracedManagementEndpoints) {
        this.tracedManagementEndpoints = tracedManagementEndpoints == null ? Collections.emptyList() : tracedManagementEndpoints;
    }
}
