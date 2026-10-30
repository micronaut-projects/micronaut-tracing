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
                'Authorization': 'Bearer token',
                'Content-Type' : 'application/x-protobuf'
        ]
        configProperties.getMap('otel.resource.attributes') == [
                'service.name': 'explicit-service',
                'environment' : 'test'
        ]
        configProperties.getString('otel.service.name') == null

        cleanup:
        context.close()
    }

    void "existing and nested Micronaut map properties are visible as OpenTelemetry map properties"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                : 'DefaultOpenTelemetryFactorySpec',
                'otel.exporter.otlp.headers'               : 'Existing=present, ',
                'otel.exporter.otlp.headers.Authorization' : 'Bearer token',
                'otel.resource.attributes'                 : 'deployment.environment=prod, ',
                'otel.resource.attributes.service.name'    : 'explicit-service'
        ])

        when:
        context.getBean(OpenTelemetry)
        ConfigProperties configProperties = CONFIG_PROPERTIES.get()

        then:
        configProperties.getMap('otel.exporter.otlp.headers') == [
                'Existing'     : 'present',
                'Authorization': 'Bearer token'
        ]
        configProperties.getMap('otel.resource.attributes') == [
                'deployment.environment': 'prod',
                'service.name'          : 'explicit-service'
        ]

        cleanup:
        context.close()
    }

    void "nested Micronaut map property overrides duplicate existing OpenTelemetry map property key"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                : 'DefaultOpenTelemetryFactorySpec',
                'otel.exporter.otlp.headers'               : 'Authorization=old',
                'otel.exporter.otlp.headers.Authorization' : 'new'
        ])

        when:
        context.getBean(OpenTelemetry)
        ConfigProperties configProperties = CONFIG_PROPERTIES.get()

        then:
        configProperties.getMap('otel.exporter.otlp.headers') == [
                'Authorization': 'new'
        ]

        cleanup:
        context.close()
    }

    void "signal specific header maps accept nested configuration"() {
        when:
        ConfigProperties config = configProperties([
                'otel.exporter.otlp.traces.headers.Authorization' : 'Bearer traces-token',
                'otel.exporter.otlp.metrics.headers.Authorization': 'Bearer metrics-token',
                'otel.exporter.otlp.logs.headers.Authorization'   : 'Bearer logs-token'
        ])

        then:
        config.getMap('otel.exporter.otlp.traces.headers') == ['Authorization': 'Bearer traces-token']
        config.getMap('otel.exporter.otlp.metrics.headers') == ['Authorization': 'Bearer metrics-token']
        config.getMap('otel.exporter.otlp.logs.headers') == ['Authorization': 'Bearer logs-token']
        config.getString('otel.traces.exporter') == 'none'
    }

    void "any map property accepts nested configuration and the OpenTelemetry string format"() {
        expect: 'a map property no Micronaut code knows about'
        configProperties(['otel.instrumentation.common.peer-service-mapping.1.2.3.4': 'cats'])
                .getMap('otel.instrumentation.common.peer-service-mapping') == ['1.2.3.4': 'cats']
        configProperties(['otel.instrumentation.common.peer-service-mapping': '1.2.3.4=cats,dogs.example=dogs'])
                .getMap('otel.instrumentation.common.peer-service-mapping') == ['1.2.3.4': 'cats', 'dogs.example': 'dogs']
    }

    void "list properties accept a comma-separated string or a list"() {
        expect:
        configProperties(['otel.propagators': 'tracecontext,baggage']).getList('otel.propagators') == ['tracecontext', 'baggage']
        configProperties(['otel.propagators': ['baggage', 'tracecontext']]).getList('otel.propagators') == ['baggage', 'tracecontext']
    }

    void "exporters default to none"() {
        expect:
        configProperties([:]).getString('otel.traces.exporter') == 'none'
        configProperties([:]).getString('otel.metrics.exporter') == 'none'
        configProperties([:]).getString('otel.logs.exporter') == 'none'
    }

    void "application name is used as default service name when service name is not configured"() {
        expect:
        configProperties(['micronaut.application.name': 'default-app']).getString('otel.service.name') == 'default-app'
    }

    void "service name is not configured when application name is absent"() {
        expect:
        configProperties([:]).getString('otel.service.name') == null
    }

    void "application name is used as default service name when resource service name is blank"() {
        when:
        ConfigProperties config = configProperties([
                'micronaut.application.name'      : 'default-app',
                'otel.resource.attributes.service.name': '  ',
                'otel.resource.attributes.environment' : 'test'
        ])

        then:
        config.getString('otel.service.name') == 'default-app'
    }

    void "application name does not override a resource service name"() {
        expect:
        configProperties([
                'micronaut.application.name'           : 'default-app',
                'otel.resource.attributes.service.name': 'explicit-service'
        ]).getString('otel.service.name') == null
    }

    void "application name replaces blank service name property"() {
        expect:
        configProperties([
                'micronaut.application.name': 'default-app',
                'otel.service.name'         : ' '
        ]).getString('otel.service.name') == 'default-app'
    }

    void "existing map properties are normalized before nested map properties are added"() {
        expect:
        configProperties([
                'otel.exporter.otlp.headers'              : ' Existing=present, ',
                'otel.exporter.otlp.headers.Authorization': 'Bearer token'
        ]).getMap('otel.exporter.otlp.headers') == ['Existing': 'present', 'Authorization': 'Bearer token']
    }

    private ConfigProperties configProperties(Map<String, Object> properties) {
        context = ApplicationContext.run(['spec.name': 'DefaultOpenTelemetryFactorySpec'] + properties)
        context.getBean(OpenTelemetry)
        return CONFIG_PROPERTIES.get()
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
