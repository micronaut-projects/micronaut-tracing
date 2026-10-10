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

import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

/**
 * A completed span of a retained trace.
 *
 * <p>Attribute values are strings, booleans, longs, doubles or lists of them. String values longer than
 * {@code tracing.opentelemetry.inspector.max-attribute-length} are truncated.</p>
 *
 * @param spanId               the span id, as 16 lowercase hex characters
 * @param parentSpanId         the parent span id, or {@code null} for a span without a parent
 * @param remoteParent         whether the parent span is in another process
 * @param name                 the span name
 * @param kind                 the span kind, for example {@code SERVER}
 * @param startEpochNanos      the span start, in nanoseconds since the epoch
 * @param endEpochNanos        the span end, in nanoseconds since the epoch
 * @param status               the status code: {@code UNSET}, {@code OK} or {@code ERROR}
 * @param statusDescription    the status description, if any
 * @param instrumentationScope the name of the instrumentation scope that created the span
 * @param attributes           the span attributes
 * @param events               the span events, including {@code exception} events
 * @param links                the span links
 * @since 8.4.0
 */
@Serdeable
public record InspectedSpan(
    String spanId,
    @Nullable String parentSpanId,
    boolean remoteParent,
    String name,
    String kind,
    long startEpochNanos,
    long endEpochNanos,
    String status,
    @Nullable String statusDescription,
    String instrumentationScope,
    Map<String, Object> attributes,
    List<Event> events,
    List<Link> links
) {

    /**
     * @return the span duration in nanoseconds
     */
    public long durationNanos() {
        return endEpochNanos - startEpochNanos;
    }

    /**
     * @return whether the span is a local root span, a span without a parent or with a remote parent
     */
    public boolean isLocalRoot() {
        return parentSpanId == null || remoteParent;
    }

    /**
     * @return whether the span has an error status
     */
    public boolean isError() {
        return "ERROR".equals(status);
    }

    /**
     * A span event.
     *
     * @param name       the event name, for example {@code exception}
     * @param epochNanos the event time, in nanoseconds since the epoch
     * @param attributes the event attributes
     */
    @Serdeable
    public record Event(
        String name,
        long epochNanos,
        Map<String, Object> attributes
    ) {
    }

    /**
     * A span link.
     *
     * @param traceId    the trace id of the linked span
     * @param spanId     the span id of the linked span
     * @param attributes the link attributes
     */
    @Serdeable
    public record Link(
        String traceId,
        String spanId,
        Map<String, Object> attributes
    ) {
    }
}
