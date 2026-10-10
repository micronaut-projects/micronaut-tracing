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
package io.micronaut.tracing.opentelemetry.test;

import org.jspecify.annotations.NonNull;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/**
 * Access to the spans captured by the in-memory span exporter, for use from Spock or JUnit tests.
 *
 * <p>For richer assertions use {@link #assertTraces()}, which returns OpenTelemetry's AssertJ
 * {@link TracesAssert}, or {@code io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions} directly
 * on the {@link SpanData} returned here.</p>
 *
 * @since 8.4.0
 */
@Singleton
public final class TestSpans {

    /**
     * The timeout used by {@link #awaitSpans(int)}.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final long POLL_NANOS = Duration.ofMillis(10).toNanos();

    private final InMemorySpanExporter exporter;

    /**
     * @param exporter the in-memory span exporter
     */
    public TestSpans(InMemorySpanExporter exporter) {
        this.exporter = exporter;
    }

    /**
     * @return the underlying in-memory span exporter
     */
    @NonNull
    public InMemorySpanExporter exporter() {
        return exporter;
    }

    /**
     * @return the spans finished so far, in the order they ended
     */
    @NonNull
    public List<SpanData> finishedSpans() {
        return exporter.getFinishedSpanItems();
    }

    /**
     * Discards the captured spans.
     */
    public void reset() {
        exporter.reset();
    }

    /**
     * Waits up to {@link #DEFAULT_TIMEOUT} until at least {@code count} spans have finished.
     *
     * @param count the minimum number of spans
     * @return the finished spans
     * @throws AssertionError if fewer spans finished in time
     */
    @NonNull
    public List<SpanData> awaitSpans(int count) {
        return awaitSpans(count, DEFAULT_TIMEOUT);
    }

    /**
     * Waits until at least {@code count} spans have finished.
     *
     * @param count   the minimum number of spans
     * @param timeout how long to wait
     * @return the finished spans
     * @throws AssertionError if fewer spans finished in time
     */
    @NonNull
    public List<SpanData> awaitSpans(int count, @NonNull Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<SpanData> spans = finishedSpans();
        while (spans.size() < count) {
            if (System.nanoTime() - deadline >= 0) {
                throw new AssertionError("Expected at least " + count + " finished spans within " + timeout
                    + " but got " + spans.size() + ": " + names(spans));
            }
            LockSupport.parkNanos(POLL_NANOS);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + count + " spans");
            }
            spans = finishedSpans();
        }
        return spans;
    }

    /**
     * @param name the span name
     * @return the finished spans with the given name
     */
    @NonNull
    public List<SpanData> spansNamed(@NonNull String name) {
        return finishedSpans().stream().filter(span -> span.getName().equals(name)).toList();
    }

    /**
     * @param name the span name
     * @return the only finished span with the given name
     * @throws AssertionError if there is not exactly one
     */
    @NonNull
    public SpanData spanNamed(@NonNull String name) {
        List<SpanData> spans = spansNamed(name);
        if (spans.size() != 1) {
            throw new AssertionError("Expected exactly one span named '" + name + "' but found " + spans.size()
                + " among " + names(finishedSpans()));
        }
        return spans.get(0);
    }

    /**
     * @param kind the span kind
     * @return the finished spans of the given kind
     */
    @NonNull
    public List<SpanData> spansOfKind(@NonNull SpanKind kind) {
        return finishedSpans().stream().filter(span -> span.getKind() == kind).toList();
    }

    /**
     * @param parent the parent span
     * @return the finished spans whose parent is {@code parent}
     */
    @NonNull
    public List<SpanData> childrenOf(@NonNull SpanData parent) {
        return finishedSpans().stream()
            .filter(span -> span.getTraceId().equals(parent.getTraceId())
                && span.getParentSpanId().equals(parent.getSpanId()))
            .toList();
    }

    /**
     * @param span a span
     * @return the finished parent of {@code span}, if it has one
     */
    @NonNull
    public Optional<SpanData> parentOf(@NonNull SpanData span) {
        if (!span.getParentSpanContext().isValid()) {
            return Optional.empty();
        }
        return finishedSpans().stream()
            .filter(candidate -> candidate.getTraceId().equals(span.getTraceId())
                && candidate.getSpanId().equals(span.getParentSpanId()))
            .findFirst();
    }

    /**
     * Groups the finished spans by trace. Traces are ordered by their earliest span start, and the spans of each
     * trace by start time, which is the order {@link TracesAssert#hasTracesSatisfyingExactly} expects.
     *
     * @return the finished spans grouped by trace
     */
    @NonNull
    public List<List<SpanData>> traces() {
        Comparator<SpanData> byStart = Comparator.comparingLong(SpanData::getStartEpochNanos);
        Map<String, List<SpanData>> byTrace = new LinkedHashMap<>();
        finishedSpans().stream()
            .sorted(byStart)
            .forEach(span -> byTrace.computeIfAbsent(span.getTraceId(), ignored -> new ArrayList<>()).add(span));
        return List.copyOf(byTrace.values());
    }

    /**
     * @return OpenTelemetry's AssertJ assertions over {@link #traces()}
     */
    @NonNull
    public TracesAssert assertTraces() {
        return TracesAssert.assertThat(traces());
    }

    private static List<String> names(List<SpanData> spans) {
        return spans.stream().map(SpanData::getName).toList();
    }
}
