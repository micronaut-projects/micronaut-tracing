package io.micronaut.tracing.opentracing.util;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import jakarta.inject.Singleton;

@Singleton
@Requires(beans = Tracer.class)
@Requires(missingBeans = TracingInvocationInstrumenterFactory.class)
@Internal
public class OpenTracingInvocationInstrumenter implements TracingInvocationInstrumenterFactory, ReactiveInvocationInstrumenterFactory, InvocationInstrumenterFactory {

    private final Tracer tracer;

    /**
     * @param tracer invocation tracer
     */
    protected OpenTracingInvocationInstrumenter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public InvocationInstrumenter newReactiveInvocationInstrumenter() {
        return newTracingInvocationInstrumenter();
    }

    @Override
    public InvocationInstrumenter newTracingInvocationInstrumenter() {
        Span activeSpan = tracer.activeSpan();
        if (activeSpan == null) {
            return null;
        }

        return () -> {
            Scope activeScope = tracer.scopeManager().activate(activeSpan);
            return cleanup -> activeScope.close();
        };
    }

    @Override
    public InvocationInstrumenter newInvocationInstrumenter() {
        return newTracingInvocationInstrumenter();
    }
}
