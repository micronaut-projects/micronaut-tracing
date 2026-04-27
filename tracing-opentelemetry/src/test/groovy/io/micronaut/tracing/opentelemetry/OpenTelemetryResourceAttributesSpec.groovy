package io.micronaut.tracing.opentelemetry

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.PropertySource
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton
import spock.lang.Specification

class OpenTelemetryResourceAttributesSpec extends Specification {

    void 'otel resource attributes from #sourceName are attached to exported spans'() {
        given:
        ApplicationContext context = ApplicationContext.builder()
                .properties('spec.name': 'OpenTelemetryResourceAttributesSpec')
                .propertySources(propertySource)
                .start()
        InMemorySpanExporter spanExporter = context.getBean(InMemorySpanExporter)
        OpenTelemetry openTelemetry = context.getBean(OpenTelemetry)

        when:
        String configuredResourceAttributes = context.getProperty('otel.resource.attributes', String).orElse(null)
        def span = openTelemetry.getTracer('test').spanBuilder('test-span').startSpan()
        span.end()

        then:
        configuredResourceAttributes == 'deployment.environment=test,service.namespace=orders'
        spanExporter.finishedSpanItems.size() == 1
        spanExporter.finishedSpanItems[0].resource.getAttribute(AttributeKey.stringKey('deployment.environment')) == 'test'
        spanExporter.finishedSpanItems[0].resource.getAttribute(AttributeKey.stringKey('service.namespace')) == 'orders'
        spanExporter.finishedSpanItems[0].resource.getAttribute(AttributeKey.stringKey('provider.attribute')) == 'custom'

        cleanup:
        spanExporter.reset()
        context.close()

        where:
        sourceName            | propertySource
        'Micronaut property'  | PropertySource.of('test-properties', ['otel.resource.attributes': 'deployment.environment=test,service.namespace=orders'])
        'environment variable' | PropertySource.of('test-environment', ['OTEL_RESOURCE_ATTRIBUTES': 'deployment.environment=test,service.namespace=orders'], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE, null)
    }

    @Requires(property = 'spec.name', value = 'OpenTelemetryResourceAttributesSpec')
    @Factory
    static class TestFactory {

        @Singleton
        SpanProcessor spanProcessor(InMemorySpanExporter spanExporter) {
            SimpleSpanProcessor.create(spanExporter)
        }

        @Singleton
        InMemorySpanExporter inMemorySpanExporter() {
            InMemorySpanExporter.create()
        }

        @Singleton
        ResourceProvider resourceProvider() {
            () -> Resource.create(Attributes.of(AttributeKey.stringKey('provider.attribute'), 'custom'))
        }
    }
}
