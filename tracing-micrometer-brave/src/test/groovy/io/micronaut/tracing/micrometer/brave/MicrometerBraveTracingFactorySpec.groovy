package io.micronaut.tracing.micrometer.brave

import brave.propagation.B3Propagation
import brave.propagation.Propagation
import io.micrometer.tracing.BaggageInScope
import io.micrometer.tracing.BaggageManager
import io.micrometer.tracing.CurrentTraceContext
import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.brave.bridge.BraveBaggageManager
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext
import io.micrometer.tracing.brave.bridge.BravePropagator
import io.micrometer.tracing.brave.bridge.BraveTracer
import io.micrometer.tracing.propagation.Propagator
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import zipkin2.reporter.Reporter

import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

class MicrometerBraveTracingFactorySpec extends Specification {

    void 'test micrometer tracing beans are backed by brave'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'micronaut.application.name': 'micrometer-brave-test',
                'tracing.zipkin.enabled': true,
                'tracing.micrometer.baggage.remote-fields': ['x-request-id'],
                'tracing.micrometer.baggage.correlation-fields': ['tenant']
        )

        expect:
        context.getBean(CurrentTraceContext) instanceof BraveCurrentTraceContext
        context.getBean(BaggageManager) instanceof BraveBaggageManager
        context.getBean(Tracer) instanceof BraveTracer
        context.getBean(Propagator) instanceof BravePropagator
        context.getBean(Tracer).baggageFields.containsAll(['x-request-id', 'tenant'])

        cleanup:
        context.close()
    }

    void 'test remote fields are propagated and correlation fields are tagged locally'() {
        given:
        CapturingReporter reporter = new CapturingReporter()
        ApplicationContext context = ApplicationContext.builder()
                .properties(
                        'micronaut.application.name': 'micrometer-brave-test',
                        'tracing.zipkin.enabled': true,
                        'tracing.zipkin.sampler.probability': 1,
                        'tracing.micrometer.baggage.remote-fields': ['x-request-id'],
                        'tracing.micrometer.baggage.correlation-fields': ['tenant']
                )
                .singletons(reporter)
                .start()
        Tracer tracer = context.getBean(Tracer)
        Propagator propagator = context.getBean(Propagator)
        Map<String, String> carrier = [:]

        when:
        Span span = tracer.nextSpan().name('baggage-test').start()
        Tracer.SpanInScope spanInScope = tracer.withSpan(span)
        BaggageInScope remote = tracer.createBaggageInScope('x-request-id', 'req-1')
        BaggageInScope correlation = tracer.createBaggageInScope('tenant', 'acme')
        propagator.inject(tracer.currentTraceContext().context(), carrier, { Map<String, String> c, String k, String v -> c.put(k, v) } as Propagator.Setter<Map<String, String>>)
        correlation.close()
        remote.close()
        spanInScope.close()
        span.end()

        then: 'only the remote field is propagated'
        carrier['x-request-id'] == 'req-1'
        !carrier.containsKey('tenant')
        carrier.containsKey('X-B3-TraceId')

        and: 'only the correlation field is recorded as a span tag'
        reporter.spans.size() == 1
        reporter.spans[0].tags()['tenant'] == 'acme'
        !reporter.spans[0].tags().containsKey('x-request-id')

        cleanup:
        context.close()
    }

    void 'test propagation factory defaults to B3 when no baggage fields are configured'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'micronaut.application.name': 'micrometer-brave-test',
                'tracing.zipkin.enabled': true
        )

        expect:
        context.getBean(Propagation.Factory).is(B3Propagation.FACTORY)

        cleanup:
        context.close()
    }

    void 'test bridge backs off when a non brave current trace context is supplied'() {
        given: 'a user supplied current trace context that is not backed by Brave'
        CurrentTraceContext userContext = new NonBraveCurrentTraceContext()
        ApplicationContext context = ApplicationContext.builder()
                .properties(
                        'micronaut.application.name': 'micrometer-brave-test',
                        'tracing.zipkin.enabled': true
                )
                .singletons(userContext)
                .start()

        expect: 'the user bean is the current trace context and no Brave context is created'
        context.getBean(CurrentTraceContext).is(userContext)
        !context.containsBean(BraveCurrentTraceContext)

        and: 'the bridge tracer, which works with Brave trace contexts, backs off with the baggage manager and propagator'
        !context.containsBean(Tracer)
        !context.containsBean(BaggageManager)
        !context.containsBean(BraveBaggageManager)
        !context.containsBean(Propagator)
        !context.containsBean(BravePropagator)

        and: 'the user context is usable'
        context.getBean(CurrentTraceContext).context().traceId() == NonBraveCurrentTraceContext.TRACE_ID

        cleanup:
        context.close()
    }

    void 'test micrometer tracing beans are not created when disabled'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'micronaut.application.name': 'micrometer-brave-test',
                'tracing.zipkin.enabled': true,
                'tracing.micrometer.enabled': false
        )

        expect:
        !context.containsBean(Tracer)
        !context.containsBean(Propagator)

        cleanup:
        context.close()
    }

    static class CapturingReporter implements Reporter<zipkin2.Span> {

        final List<zipkin2.Span> spans = new CopyOnWriteArrayList<>()

        @Override
        void report(zipkin2.Span span) {
            spans.add(span)
        }
    }

    /**
     * A Micrometer current trace context that is not backed by Brave: its trace contexts are
     * plain {@link TraceContext} values, not {@code BraveTraceContext}s.
     */
    static class NonBraveCurrentTraceContext implements CurrentTraceContext {

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
