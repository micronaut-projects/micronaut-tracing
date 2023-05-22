package io.micronaut.tracing.opentracing.util;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

/**
 * A factory interface for tracing invocation instrumentation.
 * The factory method decides if instrumentation is needed.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
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
