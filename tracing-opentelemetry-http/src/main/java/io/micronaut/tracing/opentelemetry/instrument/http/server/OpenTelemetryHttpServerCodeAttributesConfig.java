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

/**
 * Configuration of the source code attributes ({@code code.function.name}) of the HTTP server spans,
 * added by {@link HttpServerCodeAttributesExtractor}.
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@ConfigurationProperties(OpenTelemetryHttpServerCodeAttributesConfig.PREFIX)
public class OpenTelemetryHttpServerCodeAttributesConfig {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.opentelemetry.http.server.code-attributes";

    /**
     * The property that enables the code attributes.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    /**
     * The default value of {@link #isEnabled()}.
     */
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default value of {@link #isLegacyAttributes()}.
     */
    public static final boolean DEFAULT_LEGACY_ATTRIBUTES = false;

    private boolean enabled = DEFAULT_ENABLED;
    private boolean legacyAttributes = DEFAULT_LEGACY_ATTRIBUTES;

    /**
     * @return whether the HTTP server spans of the requests routed to a controller method have the
     * {@code code.function.name} attribute
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether the HTTP server spans of the requests routed to a controller method have the
     * {@code code.function.name} attribute. Default value: {@value #DEFAULT_ENABLED}.
     *
     * @param enabled {@code false} to not add the code attributes
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return whether the deprecated {@code code.namespace} and {@code code.function} attributes are
     * also added
     */
    public boolean isLegacyAttributes() {
        return legacyAttributes;
    }

    /**
     * Whether the deprecated {@code code.namespace} (the class name) and {@code code.function} (the
     * method name) attributes are also added, for backends that do not support {@code code.function.name}
     * yet. Default value: {@value #DEFAULT_LEGACY_ATTRIBUTES}.
     *
     * @param legacyAttributes {@code true} to also add the deprecated attributes
     */
    public void setLegacyAttributes(boolean legacyAttributes) {
        this.legacyAttributes = legacyAttributes;
    }
}
