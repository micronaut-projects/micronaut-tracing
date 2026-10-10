package io.micronaut.tracing.opentelemetry.test

import io.micronaut.context.ApplicationContext
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert
import io.opentelemetry.sdk.testing.assertj.TraceAssert
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat

class OpenTelemetryTestFactorySpec extends Specification {

    @AutoCleanup
    ApplicationContext context

    void "spans are exported synchronously to the in-memory exporter"() {
        given:
        context = ApplicationContext.run()
        TestSpans spans = context.getBean(TestSpans)
        Tracer tracer = context.getBean(OpenTelemetry).getTracer("test")

        when:
        def parent = tracer.spanBuilder("parent").setSpanKind(SpanKind.SERVER).startSpan()
        try (Scope ignored = parent.makeCurrent()) {
            tracer.spanBuilder("child").setSpanKind(SpanKind.CLIENT).startSpan().end()
        }
        parent.end()

        then:
        spans.finishedSpans()*.name == ["child", "parent"]
        spans.exporter().is(context.getBean(InMemorySpanExporter))
        spans.spanNamed("parent").kind == SpanKind.SERVER
        spans.spansOfKind(SpanKind.CLIENT)*.name == ["child"]
        spans.childrenOf(spans.spanNamed("parent"))*.name == ["child"]
        spans.parentOf(spans.spanNamed("child")).get().name == "parent"
        !spans.parentOf(spans.spanNamed("parent")).present
        spans.traces().size() == 1
        spans.traces()[0]*.name == ["parent", "child"]

        and:
        // Groovy closures must be coerced to Consumer, otherwise the Iterable overload is chosen
        spans.assertTraces().hasTracesSatisfyingExactly({ TraceAssert trace ->
            trace.hasSpansSatisfyingExactly(
                { SpanDataAssert span -> span.hasName("parent").hasNoParent() } as Consumer<SpanDataAssert>,
                { SpanDataAssert span -> span.hasName("child").hasParent(spans.spanNamed("parent")) } as Consumer<SpanDataAssert>
            )
        } as Consumer<TraceAssert>)
        assertThat(spans.spanNamed("child")).hasKind(SpanKind.CLIENT)

        when:
        spans.reset()

        then:
        spans.finishedSpans().empty
    }

    void "awaitSpans waits for spans ended on another thread"() {
        given:
        context = ApplicationContext.run()
        TestSpans spans = context.getBean(TestSpans)
        Tracer tracer = context.getBean(OpenTelemetry).getTracer("test")

        when:
        CompletableFuture.runAsync {
            Thread.sleep(100)
            tracer.spanBuilder("async").startSpan().end()
        }

        then:
        spans.awaitSpans(1)*.name == ["async"]

        when:
        spans.awaitSpans(2, Duration.ofMillis(50))

        then:
        AssertionError e = thrown()
        e.message.contains("Expected at least 2 finished spans")
        e.message.contains("async")
    }

    void "spanNamed fails when the span is missing"() {
        given:
        context = ApplicationContext.run()

        when:
        context.getBean(TestSpans).spanNamed("missing")

        then:
        AssertionError e = thrown()
        e.message.contains("Expected exactly one span named 'missing' but found 0")
    }

    void "metrics are collected by the in-memory metric reader"() {
        given:
        context = ApplicationContext.run()
        def meter = context.getBean(OpenTelemetry).getMeter("test")

        when:
        meter.counterBuilder("test.counter").build().add(3)

        then:
        context.getBean(InMemoryMetricReader).collectAllMetrics()*.name == ["test.counter"]
    }

    void "the metric reader registration can be disabled"() {
        given:
        context = ApplicationContext.run(["tracing.opentelemetry.test.metrics.enabled": false])
        def meter = context.getBean(OpenTelemetry).getMeter("test")

        when:
        meter.counterBuilder("test.counter").build().add(3)

        then:
        context.getBean(InMemoryMetricReader).collectAllMetrics().empty
        testCustomizers(context).size() == 1
    }

    void "the test support can be disabled"() {
        given:
        context = ApplicationContext.run(["tracing.opentelemetry.test.enabled": false])

        expect:
        !context.containsBean(TestSpans)
        !context.containsBean(InMemorySpanExporter)
        !context.containsBean(InMemoryMetricReader)
        testCustomizers(context).empty
        context.getBean(OpenTelemetry).getTracer("test").spanBuilder("span").startSpan().spanContext.valid
    }

    // other modules register customizers too (e.g. the sampler customizer of tracing-opentelemetry),
    // so only count the ones of the test support
    private static Collection<OpenTelemetryBuilderCustomizer> testCustomizers(ApplicationContext context) {
        context.getBeansOfType(OpenTelemetryBuilderCustomizer).findAll {
            it.getClass().name.startsWith(OpenTelemetryTestFactory.package.name + '.')
        }
    }
}
