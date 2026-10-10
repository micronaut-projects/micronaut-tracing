package io.micronaut.tracing.nativetest;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.inspector.InspectedTrace;
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector;
import io.micronaut.tracing.opentelemetry.inspector.TraceQuery;
import io.micronaut.tracing.opentelemetry.inspector.TraceSummary;
import io.micronaut.tracing.opentelemetry.test.TestSpans;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The trace inspector and its management endpoint, whose responses are serialized with Micronaut Serialization.
 */
@MicronautTest
@Property(name = "tracing.opentelemetry.inspector.enabled", value = "true")
@Property(name = "endpoints.traces.sensitive", value = "false")
class TraceInspectorTest {

    @Inject
    EmbeddedServer server;

    @Inject
    TraceInspector inspector;

    @Inject
    TestSpans spans;

    @Test
    void tracesAreInspected() {
        // a client outside the application context, so that the trace starts with the server span
        try (HttpClient httpClient = HttpClient.create(server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();
            client.retrieve(HttpRequest.GET("/greet/inspected"));
            // the server span of /greet, the @NewSpan span, the client span and the server span of /downstream
            spans.awaitSpans(4);

            List<TraceSummary> summaries = inspector.traces(TraceQuery.builder().name("greet").build());
            assertEquals(1, summaries.size(), () -> "Traces: " + inspector.traces());
            TraceSummary summary = summaries.get(0);
            assertEquals("GET /greet/{name}", summary.name());
            assertEquals("/greet/{name}", summary.httpRoute());
            assertEquals(200, summary.httpStatus());
            assertEquals(4, summary.spanCount());

            List<TraceSummary> listed = client.retrieve(HttpRequest.GET("/traces?name=greet"), Argument.listOf(TraceSummary.class));
            assertEquals(List.of(summary.traceId()), listed.stream().map(TraceSummary::traceId).toList());

            InspectedTrace trace = client.retrieve(HttpRequest.GET("/traces/" + summary.traceId()), InspectedTrace.class);
            assertEquals(summary.traceId(), trace.summary().traceId());
            assertEquals(4, trace.spans().size());
        }
    }
}
