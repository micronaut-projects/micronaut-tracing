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
package io.micronaut.tracing.opentelemetry.inspector.endpoint;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.management.endpoint.annotation.Delete;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.tracing.opentelemetry.inspector.InspectedTrace;
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector;
import io.micronaut.tracing.opentelemetry.inspector.TraceQuery;
import io.micronaut.tracing.opentelemetry.inspector.TraceSummary;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Exposes the recent traces of the {@link TraceInspector} at {@code /traces}.
 *
 * <ul>
 *     <li>{@code GET /traces} lists the trace summaries, newest first. Query parameters: {@code name},
 *     {@code status}, {@code error}, {@code minDuration} (for example {@code 100ms}), {@code since}
 *     (an ISO-8601 instant) and {@code limit}.</li>
 *     <li>{@code GET /traces/{traceId}} returns a trace with all its spans, or 404.</li>
 *     <li>{@code DELETE /traces} discards all traces.</li>
 * </ul>
 *
 * <p>Available only when the trace inspector is enabled. Sensitive by default.</p>
 *
 * @since 8.4.0
 */
@Endpoint(id = TracesEndpoint.ID, defaultSensitive = true)
@Requires(bean = TraceInspector.class)
public class TracesEndpoint {

    /**
     * The endpoint id.
     */
    public static final String ID = "traces";

    private final TraceInspector inspector;

    /**
     * @param inspector the trace inspector
     */
    public TracesEndpoint(TraceInspector inspector) {
        this.inspector = inspector;
    }

    /**
     * Lists the trace summaries that match the query parameters, newest first.
     *
     * @param name        text that the root span name, HTTP route or URL path contains, ignoring case
     * @param status      the HTTP response status code of the root span
     * @param error       {@code true} to list only traces with an error
     * @param minDuration the minimum trace duration
     * @param since       the earliest trace start
     * @param limit       the maximum number of summaries
     * @return the trace summaries
     */
    @Read
    public List<TraceSummary> traces(@Nullable String name,
                                     @Nullable Integer status,
                                     @Nullable Boolean error,
                                     @Nullable Duration minDuration,
                                     @Nullable Instant since,
                                     @Nullable Integer limit) {
        return inspector.traces(TraceQuery.builder()
            .name(name)
            .httpStatus(status)
            .errorsOnly(Boolean.TRUE.equals(error))
            .minDuration(minDuration)
            .since(since)
            .limit(limit == null ? 0 : limit)
            .build());
    }

    /**
     * Returns a trace with all its spans.
     *
     * @param traceId the trace id
     * @return the trace, or {@code null} (404) if it is not retained
     */
    @Read
    @Nullable
    public InspectedTrace trace(@Selector String traceId) {
        return inspector.trace(traceId).orElse(null);
    }

    /**
     * Discards all traces.
     */
    @Delete
    public void clear() {
        inspector.clear();
    }
}
