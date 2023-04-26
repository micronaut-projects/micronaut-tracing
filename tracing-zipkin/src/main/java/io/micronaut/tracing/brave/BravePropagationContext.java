package io.micronaut.tracing.brave;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;

public record BravePropagationContext(CurrentTraceContext currentTraceContext, TraceContext context)
    implements ThreadPropagatedContextElement<CurrentTraceContext.Scope> {

    @Override
    public CurrentTraceContext.Scope updateThreadContext() {
        return currentTraceContext.maybeScope(context);
    }

    @Override
    public void restoreThreadContext(CurrentTraceContext.Scope oldScope) {
        oldScope.close();
    }
}
