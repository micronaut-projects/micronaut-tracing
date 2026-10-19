package io.micronaut.tracing.docs;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class OrderServiceTest {

    @Inject
    OrderService orderService;

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    InMemorySpanExporter exporter;

    @BeforeEach
    void resetExporter() {
        exporter.reset();
    }

    @Test
    void addingSpanAttributesAddsTheAttributeToTheCurrentSpan() {
        Span outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan();
        try (Scope ignored = outer.makeCurrent()) {
            assertEquals("processed 42", orderService.process("42"));
        } finally {
            outer.end();
        }

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        assertEquals("outer", spans.get(0).getName());
        assertEquals("42", spans.get(0).getAttributes().get(AttributeKey.stringKey("order.id")));
    }

    @Test
    void addingSpanAttributesCreatesNoSpan() {
        assertEquals("processed 42", orderService.process("42"));

        assertTrue(exporter.getFinishedSpanItems().isEmpty());
    }
}
