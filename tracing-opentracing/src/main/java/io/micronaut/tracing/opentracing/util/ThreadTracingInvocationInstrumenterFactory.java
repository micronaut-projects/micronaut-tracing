package io.micronaut.tracing.opentracing.util;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import jakarta.inject.Singleton;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.core.util.StringUtils.TRUE;
import static io.micronaut.tracing.opentracing.util.ThreadTracingInvocationInstrumenterFactory.PROPERTY_INSTRUMENT_THREADS;

/**
 * Enables threads tracing invocation instrumentation.
 */
@Requires(beans = TracingInvocationInstrumenterFactory.class)
@Requires(property = PROPERTY_INSTRUMENT_THREADS, value = TRUE, defaultValue = FALSE)
@Singleton
@Internal
final class ThreadTracingInvocationInstrumenterFactory implements InvocationInstrumenterFactory {

    public static final String PROPERTY_INSTRUMENT_THREADS = "tracing.instrument-threads";

    private final TracingInvocationInstrumenterFactory factory;

    /**
     * Constructor.
     *
     * @param factory factory to delegate to
     */
    ThreadTracingInvocationInstrumenterFactory(TracingInvocationInstrumenterFactory factory) {
        this.factory = factory;
    }

    /**
     * An optional invocation instrumentation.
     *
     * @return the instrumentation
     */
    @Override
    public InvocationInstrumenter newInvocationInstrumenter() {
        return factory.newTracingInvocationInstrumenter();
    }
}
