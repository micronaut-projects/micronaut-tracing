package io.micronaut.tracing.opentelemetry.interceptor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.tracing.util.TracedMethod
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AddingSpanAttributesSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
        'spec.name'           : 'AddingSpanAttributesSpec',
        'otel.register.global': false
    ])

    InMemorySpanExporter exporter = context.getBean(InMemorySpanExporter)
    Tracer tracer = context.getBean(Tracer)

    void cleanup() {
        exporter.reset()
    }

    void 'a Java @AddingSpanAttributes method adds its @SpanAttribute parameters to the current span'() {
        given:
        def service = context.getBean(AddingSpanAttributesJavaService)

        when:
        String result = inSpan { service.add('a', 'b', 'c') }

        then:
        result == 'abc'
        exporter.finishedSpanItems.size() == 1
        def span = exporter.finishedSpanItems[0]
        span.name == 'parent'
        span.attributes.get(AttributeKey.stringKey('java.attribute')) == 'a'
        span.attributes.get(AttributeKey.stringKey('named')) == 'c'
        span.attributes.get(AttributeKey.stringKey('notAnAttribute')) == null
    }

    void 'a Groovy @AddingSpanAttributes method adds its @SpanAttribute parameters to the current span'() {
        given:
        def service = context.getBean(GroovyService)

        when:
        String result = inSpan { service.add('x', 42) }

        then:
        result == 'x42'
        exporter.finishedSpanItems.size() == 1
        def span = exporter.finishedSpanItems[0]
        span.name == 'parent'
        span.attributes.get(AttributeKey.stringKey('groovy.attribute')) == 'x'
        span.attributes.get(AttributeKey.stringKey('groovy.number')) == '42'
    }

    void 'an @AddingSpanAttributes method without a current span creates no span'() {
        given:
        def service = context.getBean(AddingSpanAttributesJavaService)

        when:
        String result = service.add('a', 'b', 'c')

        then:
        result == 'abc'
        exporter.finishedSpanItems.isEmpty()
    }

    void 'the span data of an @AddingSpanAttributes method is computed at compile time'() {
        given:
        def definition = context.getBeanDefinition(AddingSpanAttributesJavaService)
        TracedMethod traced = TracedMethod.of(definition.findMethod('add', String, String, String).get())

        expect:
        traced.precomputed
        !traced.newSpan
        traced.methodName == 'add'
        traced.tagIndexes == [0, 2] as int[]
        traced.tagNames == ['java.attribute', 'named'] as String[]
    }

    private String inSpan(Closure<String> closure) {
        Span parent = tracer.spanBuilder('parent').startSpan()
        try (Scope ignored = parent.makeCurrent()) {
            return closure.call()
        } finally {
            parent.end()
        }
    }

    @Requires(property = 'spec.name', value = 'AddingSpanAttributesSpec')
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

    @Requires(property = 'spec.name', value = 'AddingSpanAttributesSpec')
    @Singleton
    static class GroovyService {

        @AddingSpanAttributes
        String add(@SpanAttribute('groovy.attribute') String value, @SpanAttribute('groovy.number') int number) {
            value + number
        }
    }
}
