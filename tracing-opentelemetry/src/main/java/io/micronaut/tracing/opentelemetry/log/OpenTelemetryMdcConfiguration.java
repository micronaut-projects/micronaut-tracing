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
package io.micronaut.tracing.opentelemetry.log;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Configuration of the automatic log correlation: the trace context of the current OpenTelemetry span
 * (and optionally baggage entries) is copied into the SLF4J {@link org.slf4j.MDC}.
 *
 * @since 8.4.0
 */
@ConfigurationProperties(OpenTelemetryMdcConfiguration.PREFIX)
public class OpenTelemetryMdcConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.opentelemetry.logging.mdc";

    /**
     * The default MDC key of the trace id, the same as the OpenTelemetry logback-mdc instrumentation.
     */
    public static final String DEFAULT_TRACE_ID_KEY = "trace_id";

    /**
     * The default MDC key of the span id, the same as the OpenTelemetry logback-mdc instrumentation.
     */
    public static final String DEFAULT_SPAN_ID_KEY = "span_id";

    /**
     * The default MDC key of the trace flags, the same as the OpenTelemetry logback-mdc instrumentation.
     */
    public static final String DEFAULT_TRACE_FLAGS_KEY = "trace_flags";

    @Nullable
    private Boolean enabled;
    private String traceIdKey = DEFAULT_TRACE_ID_KEY;
    private String spanIdKey = DEFAULT_SPAN_ID_KEY;
    private String traceFlagsKey = DEFAULT_TRACE_FLAGS_KEY;
    private List<String> baggageKeys = Collections.emptyList();

    /**
     * @return whether the MDC population is enabled, {@code null} when not configured
     */
    @Nullable
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Sets whether the trace context is copied into the MDC. When not set, it is enabled unless the
     * OpenTelemetry logback-mdc instrumentation is on the classpath (that appender adds the same keys).
     *
     * @param enabled Whether the MDC population is enabled
     */
    public void setEnabled(@Nullable Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the MDC key of the trace id
     */
    public String getTraceIdKey() {
        return traceIdKey;
    }

    /**
     * Sets the MDC key of the trace id. Default value ({@value #DEFAULT_TRACE_ID_KEY}).
     *
     * @param traceIdKey The key
     */
    public void setTraceIdKey(String traceIdKey) {
        this.traceIdKey = traceIdKey;
    }

    /**
     * @return the MDC key of the span id
     */
    public String getSpanIdKey() {
        return spanIdKey;
    }

    /**
     * Sets the MDC key of the span id. Default value ({@value #DEFAULT_SPAN_ID_KEY}).
     *
     * @param spanIdKey The key
     */
    public void setSpanIdKey(String spanIdKey) {
        this.spanIdKey = spanIdKey;
    }

    /**
     * @return the MDC key of the trace flags
     */
    public String getTraceFlagsKey() {
        return traceFlagsKey;
    }

    /**
     * Sets the MDC key of the trace flags. Default value ({@value #DEFAULT_TRACE_FLAGS_KEY}).
     *
     * @param traceFlagsKey The key
     */
    public void setTraceFlagsKey(String traceFlagsKey) {
        this.traceFlagsKey = traceFlagsKey;
    }

    /**
     * @return the baggage entries copied into the MDC
     */
    public List<String> getBaggageKeys() {
        return baggageKeys;
    }

    /**
     * Sets the names of the baggage entries copied into the MDC, under the same key. Default: none.
     *
     * @param baggageKeys The baggage entry names
     */
    public void setBaggageKeys(@Nullable List<String> baggageKeys) {
        this.baggageKeys = baggageKeys == null ? Collections.emptyList() : List.copyOf(baggageKeys);
    }
}
