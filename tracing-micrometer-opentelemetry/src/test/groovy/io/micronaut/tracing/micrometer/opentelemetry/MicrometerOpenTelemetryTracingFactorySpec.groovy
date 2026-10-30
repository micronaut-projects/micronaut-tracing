package io.micronaut.tracing.micrometer.opentelemetry

import io.micrometer.tracing.BaggageManager
import io.micrometer.tracing.CurrentTraceContext
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.otel.bridge.OtelBaggageManager
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import io.micrometer.tracing.otel.bridge.OtelPropagator
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.micrometer.tracing.propagation.Propagator
import io.micronaut.context.ApplicationContext
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import spock.lang.Specification

class MicrometerOpenTelemetryTracingFactorySpec extends Specification {

    void 'test micrometer tracing beans are backed by opentelemetry'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'micronaut.application.name': 'micrometer-otel-test',
                'tracing.micrometer.baggage.remote-fields': ['x-request-id'],
                'tracing.micrometer.baggage.correlation-fields': ['tenant']
        )

        expect:
        context.getBean(CurrentTraceContext) instanceof OtelCurrentTraceContext
        context.getBean(BaggageManager) instanceof OtelBaggageManager
        context.getBean(Tracer) instanceof OtelTracer
        context.getBean(Propagator) instanceof OtelPropagator
        // the OpenTelemetry bridge reports only the remote fields as baggage fields
        context.getBean(Tracer).baggageFields == ['x-request-id']

        cleanup:
        context.close()
    }

    void 'test correlation fields are tagged and baggage is propagated by the opentelemetry propagators'() {
        given:
        InMemorySpanExporter exporter = InMemorySpanExporter.create()
        ApplicationContext context = ApplicationContext.builder()
                .properties(
                        'micronaut.application.name': 'micrometer-otel-test',
                        'tracing.micrometer.baggage.remote-fields': ['x-request-id'],
                        'tracing.micrometer.baggage.correlation-fields': ['tenant']
                )
                .singletons((SpanProcessor) SimpleSpanProcessor.create(exporter))
                .start()
        Tracer tracer = context.getBean(Tracer)
        Propagator propagator = context.getBean(Propagator)
        Map<String, String> carrier = [:]

        when:
        def span = tracer.nextSpan().name('baggage').start()
        try (def ignored = tracer.withSpan(span)) {
            try (def ignoredRequestId = tracer.createBaggageInScope('x-request-id', 'request-1')) {
                try (def ignoredTenant = tracer.createBaggageInScope('tenant', 'acme')) {
                    propagator.inject(tracer.currentTraceContext().context(), carrier, { Map<String, String> c, String key, String value -> c.put(key, value) } as Propagator.Setter)
                }
            }
        } finally {
            span.end()
        }
        def spans = exporter.finishedSpanItems

        then: 'the correlation field is a tag of the span and the remote field is propagated'
        spans.size() == 1
        spans[0].attributes.asMap().collectEntries { [(it.key.key): it.value] } == [tenant: 'acme']
        carrier.traceparent
        carrier.baggage.contains('x-request-id=request-1')

        and: 'the default OpenTelemetry baggage propagator sends all baggage, the correlation field included'
        carrier.baggage.contains('tenant=acme')

        cleanup:
        context.close()
    }

    void 'test user supplied non otel current trace context does not break startup'() {
        given:
        ApplicationContext context = ApplicationContext.builder()
                .properties('micronaut.application.name': 'micrometer-otel-test')
                .singletons(CurrentTraceContext.NOOP)
                .start()

        expect: 'the user bean is used and the bridge tracer, which needs an OtelCurrentTraceContext, backs off'
        context.getBean(CurrentTraceContext).is(CurrentTraceContext.NOOP)
        !context.containsBean(OtelCurrentTraceContext)
        context.getBean(BaggageManager) instanceof OtelBaggageManager
        !context.containsBean(Tracer)
        context.getBean(Propagator) instanceof OtelPropagator

        cleanup:
        context.close()
    }

    void 'test micrometer tracing beans are not created when disabled'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'micronaut.application.name': 'micrometer-otel-test',
                'tracing.micrometer.enabled': false
        )

        expect:
        !context.containsBean(Tracer)
        !context.containsBean(Propagator)

        cleanup:
        context.close()
    }
}
