package io.micronaut.tracing.opentracing;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;

public record OpenTracingPropagationContext(Tracer tracer, Span span) implements ThreadPropagatedContextElement<Scope> {

    @Override
    public Scope updateThreadContext() {
        return tracer.activateSpan(span);
    }

    @Override
    public void restoreThreadContext(Scope oldScope) {
        oldScope.close();
    }
}
