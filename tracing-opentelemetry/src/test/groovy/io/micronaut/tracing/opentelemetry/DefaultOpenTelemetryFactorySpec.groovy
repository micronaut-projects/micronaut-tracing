package io.micronaut.tracing.opentelemetry

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction

class DefaultOpenTelemetryFactorySpec extends Specification {

    private static final AttributeKey<String> VALUE = AttributeKey.stringKey("value")
    private static final AtomicReference<ConfigProperties> CONFIG_PROPERTIES = new AtomicReference<>()

    ApplicationContext context
    OpenTelemetrySdk globalOpenTelemetry

    def cleanup() {
        context?.close()
        globalOpenTelemetry?.close()
        GlobalOpenTelemetry.resetForTest()
        CONFIG_PROPERTIES.set(null)
    }

    void "uses pre-registered GlobalOpenTelemetry for Micronaut spans"() {
        given:
        def exporter = InMemorySpanExporter.create()
        globalOpenTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build())
            .buildAndRegisterGlobal()

        when:
        context = ApplicationContext.run([
            'micronaut.application.name': 'test-app',
            'otel.register.global'      : 'true',
            'spec.name'                 : 'DefaultOpenTelemetryFactorySpec',
        ], Environment.TEST)

        def result = context.getBean(TestService).invoke('test-value')

        then:
        result == 'test-value'
        context.getBean(OpenTelemetry).is(GlobalOpenTelemetry.get())
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].name.contains('#invoke')
        exporter.finishedSpanItems[0].attributes.get(VALUE) == 'test-value'
    }

    void "builds local OpenTelemetry when the global is initialized to noop"() {
        given:
        OpenTelemetry noopGlobal = GlobalOpenTelemetry.get()

        when:
        context = ApplicationContext.run([
            'otel.register.global': 'true'
        ], Environment.TEST)

        then:
        context.getBean(OpenTelemetry) != noopGlobal
        context.getBean(OpenTelemetry).tracerProvider != TracerProvider.noop()
    }

    void "nested Micronaut properties are visible as OpenTelemetry map properties"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                  : 'DefaultOpenTelemetryFactorySpec',
                'otel.exporter.otlp.headers.Authorization'   : 'Bearer token',
                'otel.exporter.otlp.headers.Content-Type'    : 'application/x-protobuf',
                'otel.resource.attributes.service.name'      : 'explicit-service',
                'otel.resource.attributes.environment'       : 'test'
        ])

        when:
        context.getBean(OpenTelemetry)
        ConfigProperties configProperties = CONFIG_PROPERTIES.get()

        then:
        configProperties.getMap('otel.exporter.otlp.headers') == [
                'authorization': 'Bearer token',
                'content-type' : 'application/x-protobuf'
        ]
        configProperties.getMap('otel.resource.attributes') == [
                'service.name': 'explicit-service',
                'environment' : 'test'
        ]
        configProperties.getString('otel.service.name') == null

        cleanup:
        context.close()
    }

    void "nested map properties are converted to OpenTelemetry map property format"() {
        given:
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration()
        applicationConfiguration.name = 'default-app'

        when:
        Map<String, String> properties = DefaultOpenTelemetryFactory.resolveOpenTelemetryProperties(
                applicationConfiguration,
                [
                        'exporter.otlp.headers.Authorization'       : 'Bearer token',
                        'exporter.otlp.headers.Content-Type'        : 'application/x-protobuf',
                        'exporter.otlp.traces.headers.Authorization': 'Bearer traces-token',
                        'exporter.otlp.metrics.headers.Authorization': 'Bearer metrics-token',
                        'exporter.otlp.logs.headers.Authorization'  : 'Bearer logs-token',
                        'resource.attributes.service.name'          : 'explicit-service',
                        'resource.attributes.environment'           : 'test',
                        'traces.exporter'                           : 'otlp'
                ]
        )

        then:
        properties['otel.exporter.otlp.headers'] == 'Authorization=Bearer token,Content-Type=application/x-protobuf'
        properties['otel.exporter.otlp.traces.headers'] == 'Authorization=Bearer traces-token'
        properties['otel.exporter.otlp.metrics.headers'] == 'Authorization=Bearer metrics-token'
        properties['otel.exporter.otlp.logs.headers'] == 'Authorization=Bearer logs-token'
        properties['otel.resource.attributes'] == 'service.name=explicit-service,environment=test'
        properties['otel.traces.exporter'] == 'otlp'
        !properties.containsKey('otel.exporter.otlp.headers.Authorization')
        !properties.containsKey('otel.resource.attributes.service.name')
        !properties.containsKey('otel.service.name')
    }

    void "application name is used as default service name when service name is not configured"() {
        given:
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration()
        applicationConfiguration.name = 'default-app'

        when:
        Map<String, String> properties = DefaultOpenTelemetryFactory.resolveOpenTelemetryProperties(
                applicationConfiguration,
                [:]
        )

        then:
        properties['otel.service.name'] == 'default-app'
    }

    void "service name is not configured when application name is absent"() {
        given:
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration()

        when:
        Map<String, String> properties = DefaultOpenTelemetryFactory.resolveOpenTelemetryProperties(
                applicationConfiguration,
                [:]
        )

        then:
        !properties.containsKey('otel.service.name')
    }

    void "application name is used as default service name when resource service name is blank"() {
        given:
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration()
        applicationConfiguration.name = 'default-app'

        when:
        Map<String, String> properties = DefaultOpenTelemetryFactory.resolveOpenTelemetryProperties(
                applicationConfiguration,
                [
                        'resource.attributes.service.name': '  ',
                        'resource.attributes.environment' : 'test'
                ]
        )

        then:
        properties['otel.resource.attributes'] == 'service.name=  ,environment=test'
        properties['otel.service.name'] == 'default-app'
    }

    void "application name replaces blank service name property"() {
        given:
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration()
        applicationConfiguration.name = 'default-app'

        when:
        Map<String, String> properties = DefaultOpenTelemetryFactory.resolveOpenTelemetryProperties(
                applicationConfiguration,
                [
                        'service.name': ' '
                ]
        )

        then:
        properties['otel.service.name'] == 'default-app'
    }

    void "existing map properties are normalized before nested map properties are appended"() {
        given:
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration()
        applicationConfiguration.name = 'default-app'

        when:
        Map<String, String> properties = DefaultOpenTelemetryFactory.resolveOpenTelemetryProperties(
                applicationConfiguration,
                [
                        'exporter.otlp.headers'              : ' Existing=present, ',
                        'exporter.otlp.headers.Authorization': 'Bearer token'
                ]
        )

        then:
        properties['otel.exporter.otlp.headers'] == 'Existing=present,Authorization=Bearer token'
    }

    @Factory
    @Requires(property = 'spec.name', value = 'DefaultOpenTelemetryFactorySpec')
    static class ConfigPropertiesCaptureFactory {

        @Singleton
        OpenTelemetryBuilderCustomizer configPropertiesCapture() {
            return { builder ->
                builder.addResourceCustomizer({ resource, configProperties ->
                    CONFIG_PROPERTIES.set(configProperties)
                    return resource
                } as BiFunction)
            } as OpenTelemetryBuilderCustomizer
        }
    }

    @Requires(property = 'spec.name', value = 'DefaultOpenTelemetryFactorySpec')
    @Singleton
    static class TestService {

        @NewSpan('invoke')
        String invoke(@SpanTag('value') String value) {
            return value
        }
    }
}
