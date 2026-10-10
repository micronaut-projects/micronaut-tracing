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
package io.micronaut.tracing.opentelemetry.instrument.websocket;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration of the tracing of the WebSocket handler methods.
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@ConfigurationProperties(OpenTelemetryWebSocketConfig.PREFIX)
public class OpenTelemetryWebSocketConfig {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.opentelemetry.websocket";

    /**
     * The property that enables the tracing of the WebSocket handler methods.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    /**
     * The default value of {@link #isEnabled()}.
     */
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return whether the invocations of the WebSocket handler methods are traced
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether the invocations of the {@code @OnOpen}, {@code @OnMessage}, {@code @OnClose} and {@code @OnError}
     * methods of the {@code @ServerWebSocket} and {@code @ClientWebSocket} classes are traced. Requires the
     * {@code micronaut-tracing-opentelemetry-annotation} annotation processor. Default value:
     * {@value #DEFAULT_ENABLED}.
     *
     * @param enabled {@code false} to not trace the WebSocket handler methods
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
