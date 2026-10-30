package io.micronaut.tracing.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Property(name = "spec.name", value = "ReportJobTest")
@Property(name = "tracing.opentelemetry.test.reset-before-each", value = "false") // the job runs at startup
@MicronautTest(startApplication = false)
class ReportJobTest {

    @Inject
    InMemorySpanExporter exporter;

    @Test
    void eachRunOfTheScheduledMethodIsARootSpan() throws InterruptedException {
        SpanData span = awaitSpan("ReportJob.generate");

        assertEquals(SpanKind.INTERNAL, span.getKind());
        assertFalse(span.getParentSpanContext().isValid());
        assertEquals(ReportJob.class.getName() + ".generate",
            span.getAttributes().get(AttributeKey.stringKey("code.function.name")));
        assertEquals("5m", span.getAttributes().get(AttributeKey.stringKey("micronaut.scheduled.fixed_delay")));
    }

    private SpanData awaitSpan(String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<SpanData> span = exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst();
            if (span.isPresent()) {
                return span.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("No span named " + name);
    }
}
