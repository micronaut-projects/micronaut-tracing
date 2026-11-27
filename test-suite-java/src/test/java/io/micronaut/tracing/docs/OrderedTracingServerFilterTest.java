package io.micronaut.tracing.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Property(name = "spec.name", value = "OrderedTracingServerFilterTest")
@MicronautTest
class OrderedTracingServerFilterTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemorySpanExporter exporter;

    @BeforeEach
    void resetExporter() {
        exporter.reset();
    }

    @Test
    void theFilterRecordsToTheServerSpanInRequestAndResponseFilters() throws InterruptedException {
        assertEquals("Hello", client.toBlocking().retrieve("/api/hello"));

        SpanData serverSpan = awaitServerSpan();
        assertEquals("GET /api/hello", serverSpan.getName());
        assertEquals("value", serverSpan.getAttributes().get(AttributeKey.stringKey("example.attribute")));
        assertEquals("value", serverSpan.getAttributes().get(AttributeKey.stringKey("example.response.attribute")));
    }

    // the server span is finished after the response filters ran, which may happen after the client got the response
    private SpanData awaitServerSpan() throws InterruptedException {
        SpanData serverSpan = null;
        for (int i = 0; i < 100 && serverSpan == null; i++) {
            List<SpanData> spans = exporter.getFinishedSpanItems();
            serverSpan = spans.stream().filter(span -> span.getKind() == SpanKind.SERVER).findFirst().orElse(null);
            if (serverSpan == null) {
                Thread.sleep(50);
            }
        }
        assertNotNull(serverSpan, "no server span was exported");
        return serverSpan;
    }

    @Controller("/api")
    @Requires(property = "spec.name", value = "OrderedTracingServerFilterTest")
    static class HelloController {

        @Get("/hello")
        String hello() {
            return "Hello";
        }
    }
}
