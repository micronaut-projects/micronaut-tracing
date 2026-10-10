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
package io.micronaut.tracing.opentelemetry.inspector;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration of the trace inspector.
 *
 * @since 8.4.0
 */
@ConfigurationProperties(TraceInspectorConfiguration.PREFIX)
public class TraceInspectorConfiguration {

    /**
     * The property prefix.
     */
    public static final String PREFIX = "tracing.opentelemetry.inspector";

    /**
     * Enables the trace inspector. Defaults to {@code true} in the {@code dev} environment and {@code false}
     * otherwise.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    /**
     * The default maximum number of completed traces retained.
     */
    public static final int DEFAULT_MAX_TRACES = 200;

    /**
     * The default maximum number of traces waiting for their local root span to end.
     */
    public static final int DEFAULT_MAX_PENDING_TRACES = 100;

    /**
     * The default maximum number of spans retained per trace.
     */
    public static final int DEFAULT_MAX_SPANS_PER_TRACE = 2_000;

    /**
     * The default maximum length of string attribute values.
     */
    public static final int DEFAULT_MAX_ATTRIBUTE_LENGTH = 1_024;

    private boolean enabled;
    private int maxTraces = DEFAULT_MAX_TRACES;
    private int maxPendingTraces = DEFAULT_MAX_PENDING_TRACES;
    private int maxSpansPerTrace = DEFAULT_MAX_SPANS_PER_TRACE;
    private int maxAttributeLength = DEFAULT_MAX_ATTRIBUTE_LENGTH;

    /**
     * @return whether the trace inspector is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables the trace inspector. Defaults to {@code true} in the {@code dev} environment and {@code false}
     * otherwise.
     *
     * @param enabled whether the trace inspector is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the maximum number of completed traces retained
     */
    public int getMaxTraces() {
        return maxTraces;
    }

    /**
     * The maximum number of completed traces retained. The oldest trace is evicted when a new trace completes.
     * Default {@value #DEFAULT_MAX_TRACES}.
     *
     * @param maxTraces the maximum number of completed traces
     */
    public void setMaxTraces(int maxTraces) {
        this.maxTraces = Math.max(1, maxTraces);
    }

    /**
     * @return the maximum number of traces waiting for their local root span to end
     */
    public int getMaxPendingTraces() {
        return maxPendingTraces;
    }

    /**
     * The maximum number of traces whose spans ended before their local root span. The oldest pending trace is
     * discarded when the limit is exceeded, for example when the local root span never ends.
     * Default {@value #DEFAULT_MAX_PENDING_TRACES}.
     *
     * @param maxPendingTraces the maximum number of pending traces
     */
    public void setMaxPendingTraces(int maxPendingTraces) {
        this.maxPendingTraces = Math.max(1, maxPendingTraces);
    }

    /**
     * @return the maximum number of spans retained per trace
     */
    public int getMaxSpansPerTrace() {
        return maxSpansPerTrace;
    }

    /**
     * The maximum number of spans retained per trace. Further spans are counted as dropped, except the first local
     * root span, which is always retained. Default {@value #DEFAULT_MAX_SPANS_PER_TRACE}.
     *
     * @param maxSpansPerTrace the maximum number of spans per trace
     */
    public void setMaxSpansPerTrace(int maxSpansPerTrace) {
        this.maxSpansPerTrace = Math.max(1, maxSpansPerTrace);
    }

    /**
     * @return the maximum length of string attribute values
     */
    public int getMaxAttributeLength() {
        return maxAttributeLength;
    }

    /**
     * The maximum length of string attribute values, including the attributes of span events and links.
     * Longer values are truncated. Default {@value #DEFAULT_MAX_ATTRIBUTE_LENGTH}.
     *
     * @param maxAttributeLength the maximum length of string attribute values
     */
    public void setMaxAttributeLength(int maxAttributeLength) {
        this.maxAttributeLength = Math.max(1, maxAttributeLength);
    }
}
