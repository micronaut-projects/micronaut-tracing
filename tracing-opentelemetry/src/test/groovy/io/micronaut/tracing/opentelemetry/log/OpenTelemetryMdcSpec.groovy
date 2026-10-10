package io.micronaut.tracing.opentelemetry.log

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.tracing.annotation.NewSpan
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import jakarta.inject.Singleton
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/144')
class OpenTelemetryMdcSpec extends Specification {

    static final String SPEC_NAME = 'OpenTelemetryMdcSpec'

    @AutoCleanup
    ApplicationContext context

    @AutoCleanup('shutdownNow')
    ExecutorService executor = Executors.newSingleThreadExecutor()

    void cleanup() {
        MDC.clear()
    }

    private void start(Map<String, Object> properties = [:]) {
        context = ApplicationContext.run([
            'spec.name'           : SPEC_NAME,
            'otel.traces.exporter': 'none',
            'otel.metrics.exporter': 'none',
            'otel.logs.exporter'  : 'none',
            'otel.register.global': false
        ] + properties)
    }

    void 'the span of a @NewSpan method is in the MDC and removed afterwards'() {
        given:
        start()
        MdcService service = context.getBean(MdcService)

        when:
        Map<String, String> mdc = service.inSpan()

        then:
        mdc.trace_id == mdc.current_trace_id
        mdc.span_id == mdc.current_span_id
        mdc.trace_flags == '01'
        mdc.trace_id.length() == 32
        mdc.span_id.length() == 16

        and: 'the caller thread is left without the trace context'
        MDC.get('trace_id') == null
        MDC.get('span_id') == null
        MDC.get('trace_flags') == null
    }

    void 'nested spans restore the MDC of the outer span'() {
        given:
        start()
        MdcService service = context.getBean(MdcService)

        when:
        Map<String, String> mdc = service.outer()

        then:
        mdc.inner_trace_id == mdc.trace_id
        mdc.inner_span_id != mdc.span_id
        mdc.after_span_id == mdc.span_id
        MDC.get('span_id') == null
    }

    void 'the MDC values set before the span are restored'() {
        given:
        start()
        MDC.put('trace_id', 'previous')
        MDC.put('other', 'kept')

        when:
        Map<String, String> mdc = context.getBean(MdcService).inSpan()

        then:
        mdc.trace_id != 'previous'
        mdc.other == 'kept'
        MDC.get('trace_id') == 'previous'
        MDC.get('other') == 'kept'
    }

    void 'the trace context is in the MDC after an executor hop and removed from the executor thread'() {
        given:
        start()
        MdcService service = context.getBean(MdcService)

        when:
        Map<String, String> mdc = service.onExecutor(executor)
        Map<String, String> leftOver = executor.submit({ MdcService.snapshot() } as Callable).get()

        then:
        mdc.trace_id
        mdc.trace_id == mdc.current_trace_id
        mdc.span_id == mdc.current_span_id
        leftOver.isEmpty()
    }

    void 'a propagated context without a valid span removes the trace context from the MDC'() {
        given:
        start()
        Tracer tracer = context.getBean(Tracer)
        Span span = tracer.spanBuilder('outer').startSpan()

        when:
        Map<String, String> inside
        Map<String, String> unsampled
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(Context.root().with(span))).propagate()) {
            inside = MdcService.snapshot()
            try (PropagatedContext.Scope ignored2 = PropagatedContext.getOrEmpty()
                .plus(new OpenTelemetryPropagationContext(Context.root())).propagate()) {
                unsampled = MdcService.snapshot()
            }
            inside.after = MDC.get('span_id')
        } finally {
            span.end()
        }

        then:
        inside.span_id == span.spanContext.spanId
        inside.after == span.spanContext.spanId
        unsampled.isEmpty()
        MDC.get('span_id') == null
    }

    void 'baggage entries are copied into the MDC'() {
        given:
        start('tracing.opentelemetry.logging.mdc.baggage-keys': ['tenant', 'missing'])
        MdcService service = context.getBean(MdcService)

        when:
        Map<String, String> mdc
        try (Scope ignored = Baggage.builder().put('tenant', 'acme').put('other', 'x').build()
            .storeInContext(Context.current()).makeCurrent()) {
            mdc = service.withBaggage()
        }

        then:
        mdc.tenant == 'acme'
        !mdc.containsKey('missing')
        !mdc.containsKey('other')
        mdc.trace_id
        MDC.get('tenant') == null
    }

    void 'the MDC keys are configurable'() {
        given:
        start(
            'tracing.opentelemetry.logging.mdc.trace-id-key': 'traceId',
            'tracing.opentelemetry.logging.mdc.span-id-key': 'spanId',
            'tracing.opentelemetry.logging.mdc.trace-flags-key': 'traceFlags'
        )

        when:
        Map<String, String> mdc = context.getBean(MdcService).all()

        then:
        mdc.traceId == mdc.current_trace_id
        mdc.spanId == mdc.current_span_id
        mdc.traceFlags == '01'
        !mdc.containsKey('trace_id')
        !mdc.containsKey('span_id')
        !mdc.containsKey('trace_flags')
    }

    void 'the MDC population can be disabled'() {
        given:
        start('tracing.opentelemetry.logging.mdc.enabled': false)

        when:
        Map<String, String> mdc = context.getBean(MdcService).inSpan()

        then:
        !context.containsBean(OpenTelemetryMdcInstaller)
        MdcTraceCorrelation.current() == null
        mdc.current_span_id
        !mdc.containsKey('trace_id')
        !mdc.containsKey('span_id')
    }

    void 'the MDC population is disabled with OpenTelemetry'() {
        given:
        start('micronaut.otel.enabled': false)

        expect:
        !context.containsBean(OpenTelemetryMdcInstaller)
        MdcTraceCorrelation.current() == null
    }

    void 'the correlation is uninstalled when the context is closed'() {
        given:
        start()

        expect:
        MdcTraceCorrelation.current() != null

        when:
        context.close()

        then:
        MdcTraceCorrelation.current() == null
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class MdcService {

        static Map<String, String> snapshot() {
            Map<String, String> mdc = MDC.copyOfContextMap
            mdc == null ? [:] : new LinkedHashMap<>(mdc)
        }

        static Map<String, String> withCurrent(Map<String, String> mdc) {
            def spanContext = Span.current().spanContext
            mdc + [current_trace_id: spanContext.traceId, current_span_id: spanContext.spanId]
        }

        @NewSpan
        Map<String, String> inSpan() {
            withCurrent(snapshot())
        }

        @NewSpan
        Map<String, String> all() {
            withCurrent(snapshot())
        }

        @NewSpan
        Map<String, String> withBaggage() {
            snapshot()
        }

        @NewSpan
        Map<String, String> outer() {
            Map<String, String> inner = inSpan()
            snapshot() + [inner_trace_id: inner.trace_id, inner_span_id: inner.span_id, after_span_id: MDC.get('span_id')]
        }

        @NewSpan
        Map<String, String> onExecutor(ExecutorService executor) {
            executor.submit(PropagatedContext.get().wrap({ withCurrent(snapshot()) } as Callable<Map<String, String>>)).get()
        }
    }
}
