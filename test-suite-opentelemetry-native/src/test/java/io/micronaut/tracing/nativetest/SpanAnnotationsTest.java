package io.micronaut.tracing.nativetest;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.test.TestSpans;
import io.micronaut.tracing.util.SpanMetadata;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class SpanAnnotationsTest {

    @Inject
    GreetingService greetingService;

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    BeanContext beanContext;

    @Inject
    TestSpans spans;

    @Test
    void spanMetadataIsComputedAtCompileTime() {
        ExecutableMethod<GreetingService, Object> greet = beanContext.getBeanDefinition(GreetingService.class)
            .findMethod("greet", String.class)
            .orElseThrow();

        AnnotationValue<SpanMetadata> metadata = greet.getAnnotation(SpanMetadata.class);
        assertNotNull(metadata, "No @SpanMetadata, the SpanMetadataVisitor did not run");
        assertEquals("greet", metadata.stringValue(SpanMetadata.MEMBER_METHOD).orElseThrow());
        assertArrayEquals(new int[] {0}, metadata.intValues(SpanMetadata.MEMBER_TAG_INDEXES));
        assertArrayEquals(new String[] {"greeting.name"}, metadata.stringValues(SpanMetadata.MEMBER_TAG_NAMES));
    }

    @Test
    void newSpanUsesTheClassAndMethodName() {
        greetingService.greet("Fred");

        SpanData span = spans.awaitSpans(1).get(0);
        assertEquals("GreetingService.greet", span.getName());
        assertEquals(SpanKind.INTERNAL, span.getKind());
        assertEquals("Fred", span.getAttributes().get(AttributeKey.stringKey("greeting.name")));
    }

    @Test
    void newSpanWithAName() {
        greetingService.customGreet("Fred");

        assertEquals("GreetingService.customGreet#custom-greeting", spans.awaitSpans(1).get(0).getName());
    }

    @Test
    void continueSpanTagsTheCurrentSpan() {
        Span outer = openTelemetry.getTracer("native-test").spanBuilder("outer").startSpan();
        try (Scope ignored = outer.makeCurrent()) {
            greetingService.continueGreet("Fred");
        } finally {
            outer.end();
        }

        SpanData span = spans.awaitSpans(1).get(0);
        assertEquals("outer", span.getName());
        assertEquals("Fred", span.getAttributes().get(AttributeKey.stringKey("greeting.continued")));
    }

    @Test
    void withSpanIsMappedToNewSpan() {
        greetingService.otelGreet("Fred");

        SpanData span = spans.awaitSpans(1).get(0);
        assertEquals("GreetingService.otelGreet#otel-greeting", span.getName());
        assertEquals("Fred", span.getAttributes().get(AttributeKey.stringKey("greeting.otel")));
    }
}
