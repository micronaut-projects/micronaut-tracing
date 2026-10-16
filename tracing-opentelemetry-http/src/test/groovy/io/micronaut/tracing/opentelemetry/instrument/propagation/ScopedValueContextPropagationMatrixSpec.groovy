package io.micronaut.tracing.opentelemetry.instrument.propagation

import io.micronaut.core.propagation.PropagatedContextConfiguration

/**
 * Runs the {@link ContextPropagationMatrixSpec} with {@code micronaut.propagation: scoped-value}, where the
 * propagated context is bound with a {@link ScopedValue} and scopes opened with
 * {@code PropagatedContext.propagate()} are not supported.
 */
class ScopedValueContextPropagationMatrixSpec extends ContextPropagationMatrixSpec {

    @Override
    Map<String, Object> configuration() {
        super.configuration() + ['micronaut.propagation': 'scoped-value']
    }

    void 'the scoped-value propagation mode is active'() {
        expect:
        PropagatedContextConfiguration.get() == PropagatedContextConfiguration.Mode.SCOPED_VALUE
    }
}
