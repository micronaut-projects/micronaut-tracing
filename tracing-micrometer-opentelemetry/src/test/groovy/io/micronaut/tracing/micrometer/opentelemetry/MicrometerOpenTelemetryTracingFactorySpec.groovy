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
        context.getBean(Tracer).baggageFields.contains('x-request-id')

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
