package io.micronaut.tracing.opentelemetry

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton
import spock.lang.Specification

class DefaultOpenTelemetryFactorySpec extends Specification {

    private static final AttributeKey<String> VALUE = AttributeKey.stringKey("value")

    ApplicationContext context
    OpenTelemetrySdk globalOpenTelemetry

    def cleanup() {
        context?.close()
        globalOpenTelemetry?.close()
        GlobalOpenTelemetry.resetForTest()
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

    @Requires(property = "spec.name", value = "DefaultOpenTelemetryFactorySpec")
    @Singleton
    static class TestService {

        @NewSpan("invoke")
        String invoke(@SpanTag("value") String value) {
            return value
        }
    }
}
