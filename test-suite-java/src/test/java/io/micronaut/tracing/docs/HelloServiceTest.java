package io.micronaut.tracing.docs;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class HelloServiceTest {

    @Inject
    HelloService helloService;

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    InMemorySpanExporter exporter;

    @BeforeEach
    void resetExporter() {
        exporter.reset();
    }

    @Test
    void newSpanCreatesASpanWithTheTaggedArgument() {
        assertEquals("Hello Fred", helloService.hello("Fred"));

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        SpanData span = spans.get(0);
        assertEquals("HelloService.hello#hello-world", span.getName());
        assertEquals(SpanKind.INTERNAL, span.getKind());
        assertEquals("Fred", span.getAttributes().get(AttributeKey.stringKey("person.name")));
    }

    @Test
    void continueSpanAddsTheTagOfTheNestedCallToTheSpan() {
        helloService.hello("Fred");

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertEquals("Hello Fred", span.getAttributes().get(AttributeKey.stringKey("hello.greeting")));
    }

    @Test
    void continueSpanTagsTheCurrentSpan() {
        Span outer = openTelemetry.getTracer("test").spanBuilder("outer").startSpan();
        try (Scope ignored = outer.makeCurrent()) {
            assertEquals("Hi", helloService.greet("Hi"));
        } finally {
            outer.end();
        }

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        assertEquals("outer", spans.get(0).getName());
        assertEquals("Hi", spans.get(0).getAttributes().get(AttributeKey.stringKey("hello.greeting")));
    }
}
