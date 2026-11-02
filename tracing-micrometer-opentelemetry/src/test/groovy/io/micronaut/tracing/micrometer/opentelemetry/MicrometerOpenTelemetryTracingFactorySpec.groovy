package io.micronaut.tracing.micrometer.opentelemetry

import io.micrometer.tracing.BaggageManager
import io.micrometer.tracing.CurrentTraceContext
import io.micrometer.tracing.TraceContext
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

import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

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

    void 'test bridge backs off when a non otel current trace context is supplied'() {
        given: 'a user supplied current trace context that is not backed by OpenTelemetry'
        CurrentTraceContext userContext = new NonOtelCurrentTraceContext()
        ApplicationContext context = ApplicationContext.builder()
                .properties('micronaut.application.name': 'micrometer-otel-test')
                .singletons(userContext)
                .start()

        expect: 'the user bean is the current trace context and no OpenTelemetry context is created'
        context.getBean(CurrentTraceContext).is(userContext)
        !context.containsBean(OtelCurrentTraceContext)

        and: 'the bridge beans that assume OpenTelemetry trace contexts back off together'
        !context.containsBean(Tracer)
        !context.containsBean(BaggageManager)
        !context.containsBean(OtelBaggageManager)
        !context.containsBean(Propagator)
        !context.containsBean(OtelPropagator)

        and: 'the user context is usable, nothing casts its trace context to an OpenTelemetry one'
        context.getBean(CurrentTraceContext).context().traceId() == NonOtelCurrentTraceContext.TRACE_ID

        cleanup:
        context.close()
    }

    void 'test otel baggage manager cannot work with a non otel current trace context'() {
        given: 'the bridge baggage manager built on a non OpenTelemetry context, as it was before the bridge backed off'
        OtelBaggageManager baggageManager = new OtelBaggageManager(new NonOtelCurrentTraceContext(), ['x-request-id'], [])

        when:
        baggageManager.getAllBaggage()

        then: 'reading baggage casts the foreign trace context to the OpenTelemetry one'
        thrown(ClassCastException)
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

    /**
     * A Micrometer current trace context that is not backed by OpenTelemetry: its trace
     * contexts are plain {@link TraceContext} values, not {@code OtelTraceContext}s.
     */
    static class NonOtelCurrentTraceContext implements CurrentTraceContext {

        static final String TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736'

        private final TraceContext current = new TraceContext() {
            @Override
            String traceId() { TRACE_ID }

            @Override
            String parentId() { null }

            @Override
            String spanId() { '00f067aa0ba902b7' }

            @Override
            Boolean sampled() { Boolean.TRUE }
        }

        @Override
        TraceContext context() { current }

        @Override
        CurrentTraceContext.Scope newScope(TraceContext context) { CurrentTraceContext.Scope.NOOP }

        @Override
        CurrentTraceContext.Scope maybeScope(TraceContext context) { CurrentTraceContext.Scope.NOOP }

        @Override
        <C> Callable<C> wrap(Callable<C> task) { task }

        @Override
        Runnable wrap(Runnable task) { task }

        @Override
        Executor wrap(Executor delegate) { delegate }

        @Override
        ExecutorService wrap(ExecutorService delegate) { delegate }
    }
}
