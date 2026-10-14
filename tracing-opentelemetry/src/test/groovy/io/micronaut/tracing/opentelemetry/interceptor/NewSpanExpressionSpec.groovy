package io.micronaut.tracing.opentelemetry.interceptor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The name of a {@code @NewSpan} given by an evaluated expression is evaluated for each invocation.
 */
class NewSpanExpressionSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run('spec.name': 'NewSpanExpressionSpec')

    void 'an evaluated span name is computed for each invocation'() {
        given:
        def service = context.getBean(ExpressionSpanService)
        def exporter = context.getBean(InMemorySpanExporter)

        when:
        service.call('alpha')
        service.call('beta')

        then:
        exporter.finishedSpanItems*.name == ['ExpressionSpanService.call#call-alpha', 'ExpressionSpanService.call#call-beta']
    }

    @Factory
    @Requires(property = 'spec.name', value = 'NewSpanExpressionSpec')
    static class ExporterFactory {

        @Singleton
        InMemorySpanExporter inMemorySpanExporter() {
            InMemorySpanExporter.create()
        }

        @Singleton
        SpanProcessor spanProcessor(InMemorySpanExporter exporter) {
            SimpleSpanProcessor.create(exporter)
        }
    }
}
