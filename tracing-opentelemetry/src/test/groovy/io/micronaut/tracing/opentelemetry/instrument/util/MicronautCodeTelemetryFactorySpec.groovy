package io.micronaut.tracing.opentelemetry.instrument.util

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class MicronautCodeTelemetryFactorySpec extends Specification {

    private static final String SPEC_NAME = "MicronautCodeTelemetryFactorySpec"
    private static final SpanContext LINKED_CONTEXT = SpanContext.create(
        "00000000000000000000000000000001",
        "0000000000000001",
        TraceFlags.getSampled(),
        TraceState.getDefault()
    )

    ApplicationContext context

    void cleanup() {
        context?.close()
        CustomCodeTelemetryFactory.reset()
    }

    void "starts ApplicationContext with default code telemetry instrumenter"() {
        when:
        context = startContext()

        then:
        context.getBean(Instrumenter, Qualifiers.byName("micronautCodeTelemetryInstrumenter"))
    }

    void "does not record HTTP client metrics for @NewSpan calls"() {
        given: "an SDK meter provider with a metric reader, so metrics would be recorded if registered"
        context = startContext()
        def metricReader = context.getBean(InMemoryMetricReader)
        def instrumenter = context.getBean(Instrumenter, Qualifiers.byName("micronautCodeTelemetryInstrumenter"))

        when:
        3.times { context.getBean(TestService).invoke() }

        then:
        context.getBean(InMemorySpanExporter).finishedSpanItems.size() == 3
        metricReader.collectAllMetrics().every { !it.name.startsWith("http.") }

        and: "no metrics listener is registered on the code instrumenter"
        (Instrumenter.getDeclaredField("operationListeners").tap { accessible = true }.get(instrumenter) as Object[]).length == 0
    }

    void "uses replacement internal span name extractor"() {
        given:
        context = startContext(["custom.code.telemetry.enabled": true])

        when:
        context.getBean(TestService).invoke()

        then:
        context.getBean(InMemorySpanExporter).finishedSpanItems*.name == ["custom-code-span"]
    }

    void "applies internal operation listeners metrics error cause and span links"() {
        given:
        context = startContext(["custom.code.telemetry.enabled": true])
        def instrumenter = context.getBean(Instrumenter, Qualifiers.byName("micronautCodeTelemetryInstrumenter")) as Instrumenter<ClassAndMethod, Object>
        def request = ClassAndMethod.create(TestService, "failure")
        def actualError = new IllegalStateException("actual")
        def wrapper = new RuntimeException("wrapper", actualError)

        when:
        def started = instrumenter.start(Context.root(), request)
        instrumenter.end(started, request, null, wrapper)

        then:
        CustomCodeTelemetryFactory.listenerStart.get() == 1
        CustomCodeTelemetryFactory.listenerEnd.get() == 1
        CustomCodeTelemetryFactory.metricsStart.get() == 1
        CustomCodeTelemetryFactory.metricsEnd.get() == 1
        CustomCodeTelemetryFactory.errorCause.get() == 1

        and:
        def span = context.getBean(InMemorySpanExporter).finishedSpanItems[0]
        span.links*.spanContext == [LINKED_CONTEXT]
        span.events[0].attributes.get(io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE) == IllegalStateException.name
    }

    private static ApplicationContext startContext(Map<String, Object> properties = [:]) {
        ApplicationContext.run([
            "spec.name"                 : SPEC_NAME,
            "otel.register.global"      : false,
            "micronaut.application.name": "test-app"
        ] + properties, Environment.TEST)
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class TestService {

        @NewSpan
        void invoke() {
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Factory
    static class TestOpenTelemetryFactory {

        @Singleton
        SpanProcessor spanProcessor(InMemorySpanExporter spanExporter) {
            SimpleSpanProcessor.create(spanExporter)
        }

        @Singleton
        IdGenerator idGenerator() {
            IdGenerator.random()
        }

        @Singleton
        InMemorySpanExporter inMemorySpanExporter() {
            InMemorySpanExporter.create()
        }

        @Singleton
        InMemoryMetricReader inMemoryMetricReader() {
            InMemoryMetricReader.create()
        }

        @Singleton
        OpenTelemetryBuilderCustomizer meterProviderCustomizer(InMemoryMetricReader metricReader) {
            { builder -> builder.addMeterProviderCustomizer({ meterProviderBuilder, ignored ->
                meterProviderBuilder.registerMetricReader(metricReader)
                meterProviderBuilder
            }) } as OpenTelemetryBuilderCustomizer
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = "custom.code.telemetry.enabled", value = "true")
    @Factory
    static class CustomCodeTelemetryFactory {

        static AtomicInteger listenerStart = new AtomicInteger()
        static AtomicInteger listenerEnd = new AtomicInteger()
        static AtomicInteger metricsStart = new AtomicInteger()
        static AtomicInteger metricsEnd = new AtomicInteger()
        static AtomicInteger errorCause = new AtomicInteger()

        static void reset() {
            listenerStart.set(0)
            listenerEnd.set(0)
            metricsStart.set(0)
            metricsEnd.set(0)
            errorCause.set(0)
        }

        @MicronautCodeTelemetryFactory.Internal
        @Singleton
        @Replaces(bean = SpanNameExtractor, factory = MicronautCodeTelemetryFactory, qualifier = MicronautCodeTelemetryFactory.Internal)
        SpanNameExtractor<ClassAndMethod> internalSpanNameExtractor() {
            { ClassAndMethod ignored -> "custom-code-span" } as SpanNameExtractor<ClassAndMethod>
        }

        @MicronautCodeTelemetryFactory.Internal
        @Singleton
        ErrorCauseExtractor errorCauseExtractor() {
            { Throwable error ->
                errorCause.incrementAndGet()
                error.cause ?: error
            } as ErrorCauseExtractor
        }

        @MicronautCodeTelemetryFactory.Internal
        @Singleton
        OperationListener operationListener() {
            new OperationListener() {
                @Override
                Context onStart(Context context, Attributes startAttributes, long startNanos) {
                    listenerStart.incrementAndGet()
                    context
                }

                @Override
                void onEnd(Context context, Attributes endAttributes, long endNanos) {
                    listenerEnd.incrementAndGet()
                }
            }
        }

        @MicronautCodeTelemetryFactory.Internal
        @Singleton
        OperationMetrics operationMetrics() {
            { ignored ->
                new OperationListener() {
                    @Override
                    Context onStart(Context context, Attributes startAttributes, long startNanos) {
                        metricsStart.incrementAndGet()
                        context
                    }

                    @Override
                    void onEnd(Context context, Attributes endAttributes, long endNanos) {
                        metricsEnd.incrementAndGet()
                    }
                }
            } as OperationMetrics
        }

        @MicronautCodeTelemetryFactory.Internal
        @Singleton
        SpanLinksExtractor<ClassAndMethod> spanLinksExtractor() {
            { SpanLinksBuilder spanLinks, Context parentContext, ClassAndMethod request ->
                spanLinks.addLink(LINKED_CONTEXT)
            } as SpanLinksExtractor<ClassAndMethod>
        }
    }
}
