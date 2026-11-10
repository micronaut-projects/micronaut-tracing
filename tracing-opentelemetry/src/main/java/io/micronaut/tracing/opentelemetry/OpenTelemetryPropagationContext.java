/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.opentelemetry;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * The element of Micronaut's {@link io.micronaut.core.propagation.PropagatedContext} that carries an OpenTelemetry
 * {@link Context}. While the propagated context is in scope, for example inside
 * {@link io.micronaut.core.propagation.PropagatedContext#propagate(java.util.function.Supplier)} or code wrapped with
 * {@link io.micronaut.core.propagation.PropagatedContext#wrap(Runnable)}, the OpenTelemetry context is current.
 *
 * <p>Integrations that make a span current for the code they call, such as messaging listeners, add the span's
 * context as this element, so that it follows Micronaut's thread hops. Reactive code finds it with
 * {@link io.micronaut.tracing.opentelemetry.utils.OpenTelemetryReactorPropagation#currentContext(reactor.util.context.ContextView)},
 * which looks the element up by this type, so another {@link ThreadPropagatedContextElement} carrying the same
 * context wouldn't be found there.</p>
 *
 * @param context The OpenTelemetry context
 * @author Denis Stepanov
 * @since 8.4.0
 */
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
