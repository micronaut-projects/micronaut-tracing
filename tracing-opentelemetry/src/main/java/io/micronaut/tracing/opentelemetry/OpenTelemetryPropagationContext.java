package io.micronaut.tracing.opentelemetry;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public record OpenTelemetryPropagationContext(Context context) implements ThreadPropagatedContextElement<Scope> {

    @Override
    public Scope updateThreadContext() {
        return context.makeCurrent();
    }

    @Override
    public void restoreThreadContext(Scope oldScope) {
        oldScope.close();
    }
}
