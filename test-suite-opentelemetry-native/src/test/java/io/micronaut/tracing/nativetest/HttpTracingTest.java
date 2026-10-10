package io.micronaut.tracing.nativetest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.test.TestSpans;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class HttpTracingTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    TestSpans spans;

    @Test
    void serverAndClientSpansFormOneTrace() {
        String body = client.toBlocking().retrieve(HttpRequest.GET("/greet/fred"));
        assertEquals("Hello fred / FRED", body);

        // the client span of this test, the server span of /greet, the @NewSpan span, the client span of the
        // declarative client and the server span of /downstream
        List<SpanData> finished = spans.awaitSpans(5);
        assertEquals(5, finished.size(), () -> "Unexpected spans: " + names(finished));
        assertEquals(1, spans.traces().size(), () -> "Expected one trace: " + names(finished));

        SpanData greetServer = serverSpan("GET /greet/{name}");
        SpanData downstreamServer = serverSpan("GET /downstream/{name}");
        SpanData testClient = spans.parentOf(greetServer).orElseThrow();
        SpanData downstreamClient = spans.parentOf(downstreamServer).orElseThrow();
        SpanData newSpan = spans.spanNamed("GreetingService.greet");

        assertEquals(SpanKind.CLIENT, testClient.getKind());
        assertEquals(SpanKind.CLIENT, downstreamClient.getKind());
        assertEquals(greetServer.getSpanId(), newSpan.getParentSpanId());
        assertEquals(greetServer.getSpanId(), downstreamClient.getParentSpanId());
        assertEquals(200L, greetServer.getAttributes().get(AttributeKey.longKey("http.response.status_code")));
        assertEquals("/greet/{name}", greetServer.getAttributes().get(AttributeKey.stringKey("http.route")));
        assertEquals("fred", newSpan.getAttributes().get(AttributeKey.stringKey("greeting.name")));
    }

    private SpanData serverSpan(String name) {
        List<SpanData> servers = spans.spansOfKind(SpanKind.SERVER);
        return servers.stream()
            .filter(span -> span.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No server span " + name + " in " + names(servers)));
    }

    private static List<String> names(List<SpanData> spans) {
        return spans.stream().map(span -> span.getName() + ": " + span.getKind()).toList();
    }
}
