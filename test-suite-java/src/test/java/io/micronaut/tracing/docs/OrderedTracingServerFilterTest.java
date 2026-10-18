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
    void theFilterTagsTheCurrentServerSpan() {
        assertEquals("ordered", client.toBlocking().retrieve("/api/ordered"));

        List<SpanData> serverSpans = exporter.getFinishedSpanItems().stream()
            .filter(span -> span.getKind() == SpanKind.SERVER)
            .toList();
        assertEquals(1, serverSpans.size());
        SpanData span = serverSpans.get(0);
        assertEquals("GET /api/ordered", span.getName());
        assertEquals("value", span.getAttributes().get(AttributeKey.stringKey("example.attribute")));
        assertEquals("value", span.getAttributes().get(AttributeKey.stringKey("example.response.attribute")));
    }

    @Requires(property = "spec.name", value = "OrderedTracingServerFilterTest")
    @Controller("/api")
    static class OrderedController {

        @Get("/ordered")
        String ordered() {
            return "ordered";
        }
    }
}
