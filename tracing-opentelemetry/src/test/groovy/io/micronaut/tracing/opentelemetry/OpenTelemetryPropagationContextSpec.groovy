package io.micronaut.tracing.opentelemetry

import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.tracing.opentelemetry.utils.OpenTelemetryReactorPropagation
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import spock.lang.Specification

import java.util.function.Supplier

class OpenTelemetryPropagationContextSpec extends Specification {

    Context spanContext = Context.root().with(Span.wrap(SpanContext.create(
        '0af7651916cd43dd8448eb211c80319c', 'b7ad6b7169203331', TraceFlags.getSampled(), TraceState.getDefault())))

    void "the OpenTelemetry context is current while the propagated context is"() {
        given:
        PropagatedContext propagated = PropagatedContext.getOrEmpty().plus(new OpenTelemetryPropagationContext(spanContext))

        when:
        String inside = propagated.propagate({ Span.current().spanContext.spanId } as Supplier<String>)

        then:
        inside == 'b7ad6b7169203331'
        !Span.current().spanContext.valid
    }

    void "the OpenTelemetry context follows a thread hop of the propagated context"() {
        given:
        PropagatedContext propagated = PropagatedContext.getOrEmpty().plus(new OpenTelemetryPropagationContext(spanContext))
        String seen = null
        Runnable task = propagated.wrap({ seen = Span.current().spanContext.spanId } as Runnable)

        when:
        Thread thread = new Thread(task)
        thread.start()
        thread.join()

        then:
        seen == 'b7ad6b7169203331'
    }

    void "reactive code finds the OpenTelemetry context by this element"() {
        given:
        PropagatedContext propagated = PropagatedContext.getOrEmpty().plus(new OpenTelemetryPropagationContext(spanContext))
        reactor.util.context.Context reactorContext = ReactorPropagation.addPropagatedContext(reactor.util.context.Context.empty(), propagated)

        expect:
        OpenTelemetryReactorPropagation.currentContext(reactorContext) == spanContext
    }
}
