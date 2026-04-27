package io.micronaut.tracing.micrometer.brave

import io.micrometer.tracing.BaggageManager
import io.micrometer.tracing.CurrentTraceContext
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.brave.bridge.BraveBaggageManager
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext
import io.micrometer.tracing.brave.bridge.BravePropagator
import io.micrometer.tracing.brave.bridge.BraveTracer
import io.micrometer.tracing.propagation.Propagator
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

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
        context.getBean(Tracer).baggageFields.contains('x-request-id')

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
}
