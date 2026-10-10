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

/**
 * A lightweight summary of a retained trace.
 *
 * <p>The HTTP fields are read from the attributes of the root span, the local root span that started first,
 * using the OpenTelemetry HTTP semantic conventions.</p>
 *
 * @param traceId          the trace id, as 32 lowercase hex characters
 * @param name             the name of the root span
 * @param serviceName      the {@code service.name} resource attribute of the root span
 * @param httpMethod       the {@code http.request.method} attribute of the root span
 * @param httpRoute        the {@code http.route} attribute of the root span
 * @param urlPath          the {@code url.path} attribute of the root span
 * @param httpStatus       the {@code http.response.status_code} attribute of the root span
 * @param startEpochNanos  the start of the earliest retained span, in nanoseconds since the epoch
 * @param durationNanos    the time from the start of the earliest to the end of the latest retained span
 * @param spanCount        the number of retained spans
 * @param droppedSpanCount the number of spans dropped because of the span limit per trace
 * @param error            whether at least one span has an error status
 * @since 8.4.0
 */
@Serdeable
public record TraceSummary(
    String traceId,
    String name,
    @Nullable String serviceName,
    @Nullable String httpMethod,
    @Nullable String httpRoute,
    @Nullable String urlPath,
    @Nullable Integer httpStatus,
    long startEpochNanos,
    long durationNanos,
    int spanCount,
    int droppedSpanCount,
    boolean error
) {
}
