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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The default {@link TraceInspector}: groups ended spans by trace id and keeps the most recent completed
 * traces in a bounded buffer.
 *
 * <p>Memory is bounded by {@link TraceInspectorConfiguration#getMaxTraces()} completed traces plus
 * {@link TraceInspectorConfiguration#getMaxPendingTraces()} pending traces, each with at most
 * {@link TraceInspectorConfiguration#getMaxSpansPerTrace()} spans (plus their first local root span), and string
 * attribute values of at most {@link TraceInspectorConfiguration#getMaxAttributeLength()} characters. The
 * number of attributes, events and links per span is bounded by the OpenTelemetry SDK span limits.</p>
 *
 * @since 8.4.0
 */
@Internal
@Singleton
public final class DefaultTraceInspector implements TraceInspector {

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> HTTP_REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
    private static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");

    private final int maxSpansPerTrace;
    private final int maxAttributeLength;
    private final Object lock = new Object();
    /**
     * Traces whose local root span has not ended yet, oldest first. Guarded by {@link #lock}.
     */
    private final LinkedHashMap<String, TraceBuffer> pending;
    /**
     * Completed traces, in completion order. Guarded by {@link #lock}.
     */
    private final LinkedHashMap<String, TraceBuffer> completed;

    /**
     * @param configuration the configuration
     */
    public DefaultTraceInspector(TraceInspectorConfiguration configuration) {
        this.maxSpansPerTrace = configuration.getMaxSpansPerTrace();
        this.maxAttributeLength = configuration.getMaxAttributeLength();
        int maxTraces = configuration.getMaxTraces();
        int maxPendingTraces = configuration.getMaxPendingTraces();
        this.pending = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TraceBuffer> eldest) {
                return size() > maxPendingTraces;
            }
        };
        this.completed = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TraceBuffer> eldest) {
                return size() > maxTraces;
            }
        };
    }

    /**
     * Records an ended span.
     *
     * @param spanData the span data
     */
    void record(SpanData spanData) {
        String traceId = spanData.getTraceId();
        InspectedSpan span = toInspectedSpan(spanData);
        String serviceName = spanData.getResource().getAttribute(SERVICE_NAME);
        synchronized (lock) {
            TraceBuffer trace = completed.get(traceId);
            if (trace != null) {
                // a late span of a retained trace, or another local root span of the same trace
                trace.add(span, serviceName, maxSpansPerTrace);
                return;
            }
            if (span.isLocalRoot()) {
                trace = pending.remove(traceId);
                if (trace == null) {
                    trace = new TraceBuffer(traceId);
                }
                trace.add(span, serviceName, maxSpansPerTrace);
                completed.put(traceId, trace);
            } else {
                trace = pending.get(traceId);
                if (trace == null) {
                    trace = new TraceBuffer(traceId);
                    pending.put(traceId, trace);
                }
                trace.add(span, serviceName, maxSpansPerTrace);
            }
        }
    }

    @Override
    public List<TraceSummary> traces(TraceQuery query) {
        List<TraceSummary> summaries = new ArrayList<>();
        synchronized (lock) {
            for (TraceBuffer trace : completed.values()) {
                summaries.add(trace.summary());
            }
        }
        Collections.reverse(summaries);
        List<TraceSummary> result = new ArrayList<>();
        int limit = query.limit();
        for (TraceSummary summary : summaries) {
            if (query.matches(summary)) {
                result.add(summary);
                if (limit > 0 && result.size() >= limit) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Optional<InspectedTrace> trace(String traceId) {
        TraceSummary summary;
        List<InspectedSpan> spans;
        synchronized (lock) {
            TraceBuffer trace = completed.get(traceId);
            if (trace == null) {
                return Optional.empty();
            }
            summary = trace.summary();
            spans = new ArrayList<>(trace.spans);
        }
        spans.sort(Comparator.comparingLong(InspectedSpan::startEpochNanos));
        return Optional.of(new InspectedTrace(summary, Collections.unmodifiableList(spans)));
    }

    @Override
    public void clear() {
        synchronized (lock) {
            pending.clear();
            completed.clear();
        }
    }

    private InspectedSpan toInspectedSpan(SpanData spanData) {
        SpanContext parent = spanData.getParentSpanContext();
        boolean hasParent = parent.isValid();
        List<EventData> eventData = spanData.getEvents();
        List<InspectedSpan.Event> events = new ArrayList<>(eventData.size());
        for (EventData event : eventData) {
            events.add(new InspectedSpan.Event(event.getName(), event.getEpochNanos(), toMap(event.getAttributes())));
        }
        List<LinkData> linkData = spanData.getLinks();
        List<InspectedSpan.Link> links = new ArrayList<>(linkData.size());
        for (LinkData link : linkData) {
            SpanContext linked = link.getSpanContext();
            links.add(new InspectedSpan.Link(linked.getTraceId(), linked.getSpanId(), toMap(link.getAttributes())));
        }
        String description = spanData.getStatus().getDescription();
        return new InspectedSpan(
            spanData.getSpanId(),
            hasParent ? parent.getSpanId() : null,
            hasParent && parent.isRemote(),
            spanData.getName(),
            spanData.getKind().name(),
            spanData.getStartEpochNanos(),
            spanData.getEndEpochNanos(),
            spanData.getStatus().getStatusCode().name(),
            description == null || description.isEmpty() ? null : truncate(description),
            spanData.getInstrumentationScopeInfo().getName(),
            toMap(spanData.getAttributes()),
            Collections.unmodifiableList(events),
            Collections.unmodifiableList(links)
        );
    }

    private Map<String, Object> toMap(Attributes attributes) {
        if (attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>(attributes.size() * 2);
        attributes.forEach((key, value) -> map.put(key.getKey(), truncateValue(value)));
        return Collections.unmodifiableMap(map);
    }

    private Object truncateValue(Object value) {
        if (value instanceof String string) {
            return truncate(string);
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String) {
            List<Object> truncated = new ArrayList<>(list.size());
            for (Object element : list) {
                truncated.add(element instanceof String string ? truncate(string) : element);
            }
            return Collections.unmodifiableList(truncated);
        }
        return value;
    }

    private String truncate(String value) {
        return value.length() > maxAttributeLength ? value.substring(0, maxAttributeLength) : value;
    }

    /**
     * The spans of one trace. Guarded by the lock of the enclosing inspector.
     */
    private static final class TraceBuffer {

        private final String traceId;
        private final List<InspectedSpan> spans = new ArrayList<>();
        private int droppedSpanCount;
        private boolean error;
        private long startEpochNanos = Long.MAX_VALUE;
        private long endEpochNanos = Long.MIN_VALUE;
        @Nullable
        private InspectedSpan root;
        @Nullable
        private String rootServiceName;

        TraceBuffer(String traceId) {
            this.traceId = traceId;
        }

        void add(InspectedSpan span, @Nullable String serviceName, int maxSpans) {
            boolean localRoot = span.isLocalRoot();
            // the first local root span is always kept so that the summary reflects the request
            if (spans.size() >= maxSpans && !(localRoot && root == null)) {
                droppedSpanCount++;
                return;
            }
            spans.add(span);
            error |= span.isError();
            startEpochNanos = Math.min(startEpochNanos, span.startEpochNanos());
            endEpochNanos = Math.max(endEpochNanos, span.endEpochNanos());
            if (localRoot && (root == null || span.startEpochNanos() < root.startEpochNanos())) {
                root = span;
                rootServiceName = serviceName;
            }
        }

        TraceSummary summary() {
            InspectedSpan summarySpan = root != null ? root : spans.get(0);
            Map<String, Object> attributes = summarySpan.attributes();
            Object status = attributes.get(HTTP_RESPONSE_STATUS_CODE.getKey());
            return new TraceSummary(
                traceId,
                summarySpan.name(),
                rootServiceName,
                stringAttribute(attributes, HTTP_REQUEST_METHOD),
                stringAttribute(attributes, HTTP_ROUTE),
                stringAttribute(attributes, URL_PATH),
                status instanceof Number number ? number.intValue() : null,
                startEpochNanos,
                endEpochNanos - startEpochNanos,
                spans.size(),
                droppedSpanCount,
                error
            );
        }

        @Nullable
        private static String stringAttribute(Map<String, Object> attributes, AttributeKey<String> key) {
            return attributes.get(key.getKey()) instanceof String value ? value : null;
        }
    }
}
