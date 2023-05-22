package io.micronaut.tracing.opentracing.util;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

@Experimental
public interface TracingInvocationInstrumenterFactory {

    /**
     * An optional instrumentation.
     *
     * @return an instrumentation, or null if none is necessary
     */
    @Nullable
    InvocationInstrumenter newTracingInvocationInstrumenter();
}
