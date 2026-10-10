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

import io.micronaut.core.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * A query for {@link TraceInspector#traces(TraceQuery)}. All criteria are optional and combined with AND.
 *
 * @param name        matches traces whose root span name, HTTP route or URL path contains this text, ignoring case
 * @param httpStatus  matches traces whose root span has this HTTP response status code
 * @param errorsOnly  matches only traces with at least one span with an error status
 * @param minDuration matches traces lasting at least this long
 * @param since       matches traces that started at or after this instant
 * @param limit       the maximum number of summaries returned, newest first; {@code 0} or less for no limit
 * @since 8.4.0
 */
public record TraceQuery(
    @Nullable String name,
    @Nullable Integer httpStatus,
    boolean errorsOnly,
    @Nullable Duration minDuration,
    @Nullable Instant since,
    int limit
) {

    private static final TraceQuery ALL = new TraceQuery(null, null, false, null, null, 0);

    /**
     * @return a query matching all retained traces
     */
    public static TraceQuery all() {
        return ALL;
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param summary the trace summary
     * @return whether the summary matches the query
     */
    public boolean matches(TraceSummary summary) {
        if (errorsOnly && !summary.error()) {
            return false;
        }
        if (httpStatus != null && !httpStatus.equals(summary.httpStatus())) {
            return false;
        }
        if (minDuration != null && summary.durationNanos() < minDuration.toNanos()) {
            return false;
        }
        if (since != null) {
            long sinceNanos = since.getEpochSecond() * 1_000_000_000L + since.getNano();
            if (summary.startEpochNanos() < sinceNanos) {
                return false;
            }
        }
        if (name != null && !name.isEmpty()) {
            String needle = name.toLowerCase(Locale.ROOT);
            return contains(summary.name(), needle)
                || contains(summary.httpRoute(), needle)
                || contains(summary.urlPath(), needle);
        }
        return true;
    }

    private static boolean contains(@Nullable String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Builds a {@link TraceQuery}.
     */
    public static final class Builder {

        private String name;
        private Integer httpStatus;
        private boolean errorsOnly;
        private Duration minDuration;
        private Instant since;
        private int limit;

        private Builder() {
        }

        /**
         * @param name text that the root span name, HTTP route or URL path contains, ignoring case
         * @return this builder
         */
        public Builder name(@Nullable String name) {
            this.name = name;
            return this;
        }

        /**
         * @param httpStatus the HTTP response status code of the root span
         * @return this builder
         */
        public Builder httpStatus(@Nullable Integer httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        /**
         * @param errorsOnly whether to match only traces with an error
         * @return this builder
         */
        public Builder errorsOnly(boolean errorsOnly) {
            this.errorsOnly = errorsOnly;
            return this;
        }

        /**
         * @param minDuration the minimum trace duration
         * @return this builder
         */
        public Builder minDuration(@Nullable Duration minDuration) {
            this.minDuration = minDuration;
            return this;
        }

        /**
         * @param since the earliest trace start
         * @return this builder
         */
        public Builder since(@Nullable Instant since) {
            this.since = since;
            return this;
        }

        /**
         * @param limit the maximum number of summaries; {@code 0} or less for no limit
         * @return this builder
         */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * @return the query
         */
        public TraceQuery build() {
            return new TraceQuery(name, httpStatus, errorsOnly, minDuration, since, limit);
        }
    }
}
