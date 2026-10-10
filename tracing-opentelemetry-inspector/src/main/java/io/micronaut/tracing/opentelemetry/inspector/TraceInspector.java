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

import java.util.List;
import java.util.Optional;

/**
 * Read access to the recent completed traces of the application, for developer tooling.
 *
 * <p>A trace is completed when its local root span (a span without a parent, or with a remote parent)
 * ends. Spans of a retained trace that end later are added to it.</p>
 *
 * @since 8.4.0
 */
public interface TraceInspector {

    /**
     * Returns the summaries of the retained traces that match the query, newest first.
     *
     * @param query the query
     * @return the matching trace summaries, newest first
     */
    List<TraceSummary> traces(TraceQuery query);

    /**
     * Returns the summaries of all retained traces, newest first.
     *
     * @return the trace summaries, newest first
     */
    default List<TraceSummary> traces() {
        return traces(TraceQuery.all());
    }

    /**
     * Returns a retained trace with all its spans.
     *
     * @param traceId the trace id, as 32 lowercase hex characters
     * @return the trace, or empty if it is not retained
     */
    Optional<InspectedTrace> trace(String traceId);

    /**
     * Discards all retained and pending traces.
     */
    void clear();
}
