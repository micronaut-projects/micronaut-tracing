package io.micronaut.tracing.opentelemetry.instrument.scheduling

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.CodeAttributes
import io.opentelemetry.semconv.ExceptionAttributes
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicInteger

class ScheduledSpanSpec extends Specification {

    @AutoCleanup
    ApplicationContext context

    InMemorySpanExporter exporter

    private void start(Map<String, Object> properties = [:]) {
        context = ApplicationContext.run([
            'spec.name'           : 'ScheduledSpanSpec',
            'otel.register.global': false
        ] + properties)
        exporter = context.getBean(InMemorySpanExporter)
    }

    private List<SpanData> spansNamed(String name) {
        exporter.finishedSpanItems.findAll { it.name == name }
    }

    private List<SpanData> groovySpans() {
        // the span name of a nested class starts with the name of its enclosing class
        exporter.finishedSpanItems.findAll { it.name.endsWith('GroovyJob.run') }
    }

    void 'each run of a Java scheduled method is the root span of a new trace'() {
        given:
        start()
        def job = context.getBean(ScheduledJavaJob)

        expect:
        new PollingConditions(timeout: 10).eventually {
            assert spansNamed('ScheduledJavaJob.tick').size() >= 3
        }

        when:
        def spans = spansNamed('ScheduledJavaJob.tick')

        then:
        spans.every { !it.parentSpanContext.valid }
        spans.every { it.kind == SpanKind.INTERNAL }
        spans.every { it.status.statusCode == StatusCode.UNSET }
        spans.every { it.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == ScheduledJavaJob.name + '.tick' }
        spans.every { it.attributes.get(ScheduledSpanInterceptor.FIXED_DELAY) == '50ms' }
        spans.every { it.attributes.get(ScheduledSpanInterceptor.CRON) == null }
        spans.every { it.instrumentationScopeInfo.name == 'io.micronaut.scheduling' }
        spans*.traceId.toSet().size() == spans.size()
        // the span is current while the method runs
        job.traceIds.containsAll(spans*.traceId)
    }

    void 'a failed run records the exception and has the error status'() {
        given:
        start()

        expect:
        new PollingConditions(timeout: 10).eventually {
            assert spansNamed('ScheduledJavaJob.fail').size() >= 2
        }

        when:
        def spans = spansNamed('ScheduledJavaJob.fail')

        then:
        spans.every { !it.parentSpanContext.valid }
        spans.every { it.status.statusCode == StatusCode.ERROR }
        spans.every { it.attributes.get(ScheduledSpanInterceptor.FIXED_RATE) == '50ms' }
        spans.every { span ->
            span.events.any {
                it.name == 'exception' && it.attributes.get(ExceptionAttributes.EXCEPTION_MESSAGE) == 'boom'
            }
        }
    }

    void 'each run of a Groovy scheduled method is traced, with its cron'() {
        given:
        start()

        expect:
        new PollingConditions(timeout: 10).eventually {
            assert groovySpans().size() >= 1
        }
        def span = groovySpans()[0]
        !span.parentSpanContext.valid
        span.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == GroovyJob.name + '.run'
        span.attributes.get(ScheduledSpanInterceptor.CRON) == '* * * * * *'
    }

    void 'a direct call of a scheduled method is not traced'() {
        given:
        start()
        def job = context.getBean(ScheduledJavaJob)
        Span parent = context.getBean(Tracer).spanBuilder('parent').startSpan()

        when:
        Scope scope = parent.makeCurrent()
        try {
            job.tick()
        } finally {
            scope.close()
            parent.end()
        }

        then:
        new PollingConditions(timeout: 10).eventually {
            assert spansNamed('parent').size() == 1
        }
        exporter.finishedSpanItems.every { it.parentSpanId != parent.spanContext.spanId }
    }

    void 'the scheduled method of a final class runs untraced'() {
        given:
        start()
        def job = context.getBean(FinalScheduledJavaJob)

        expect:
        new PollingConditions(timeout: 10).eventually {
            assert job.ticks.get() >= 3
            assert spansNamed('ScheduledJavaJob.tick').size() >= 1
        }
        spansNamed('FinalScheduledJavaJob.tick').isEmpty()
    }

    void 'the scheduled methods are not traced when disabled'() {
        given:
        start('tracing.opentelemetry.scheduled.enabled': false)
        def job = context.getBean(ScheduledJavaJob)

        expect:
        !context.containsBean(ScheduledSpanInterceptor)
        new PollingConditions(timeout: 10).eventually {
            assert job.ticks.get() >= 3
            assert job.failures.get() >= 2
        }
        exporter.finishedSpanItems.isEmpty()
    }

    @Requires(property = 'spec.name', value = 'ScheduledSpanSpec')
    @Factory
    static class ExporterFactory {

        @Singleton
        InMemorySpanExporter exporter() {
            InMemorySpanExporter.create()
        }

        @Singleton
        SpanProcessor processor(InMemorySpanExporter exporter) {
            SimpleSpanProcessor.create(exporter)
        }
    }

    @Requires(property = 'spec.name', value = 'ScheduledSpanSpec')
    @Singleton
    static class GroovyJob {

        final AtomicInteger runs = new AtomicInteger()

        @Scheduled(cron = '* * * * * *')
        void run() {
            runs.incrementAndGet()
        }
    }
}
