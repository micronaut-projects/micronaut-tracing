/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.tracing.opentelemetry.instrument.mongodb;

import com.mongodb.ContextProvider;
import com.mongodb.RequestContext;
import com.mongodb.client.SynchronousContextProvider;
import com.mongodb.reactivestreams.client.ReactiveContextProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.tracing.opentelemetry.utils.OpenTelemetryReactorPropagation;
import io.opentelemetry.context.Context;
import org.reactivestreams.Subscriber;
import reactor.core.CoreSubscriber;

/**
 * A {@link ReactiveContextProvider} that captures the OpenTelemetry {@link Context} of the
 * subscriber of a reactive streams driver operation: the context propagated through the Reactor
 * context by Micronaut, or else the current context.
 *
 * <p>Only used when the reactive streams driver is present. Micronaut MongoDB builds the settings of
 * the synchronous and the reactive streams clients from the same configuration, and the synchronous
 * driver requires a {@link SynchronousContextProvider}, so {@link #create(boolean)} returns a
 * provider implementing both when the synchronous driver is present too.</p>
 *
 * @since 8.4.0
 */
@Internal
class TracingReactiveContextProvider implements ReactiveContextProvider {

    /**
     * @param synchronousDriverPresent Whether the synchronous driver is present
     * @return The context provider
     */
    static ContextProvider create(boolean synchronousDriverPresent) {
        return synchronousDriverPresent ? new WithSynchronous() : new TracingReactiveContextProvider();
    }

    @Override
    public RequestContext getContext(Subscriber<?> subscriber) {
        Context context = subscriber instanceof CoreSubscriber<?> coreSubscriber
            ? OpenTelemetryReactorPropagation.currentContext(coreSubscriber.currentContext())
            : Context.current();
        return new TracingRequestContext(context);
    }

    /**
     * Also a {@link SynchronousContextProvider}, for the synchronous clients.
     */
    static final class WithSynchronous extends TracingReactiveContextProvider implements SynchronousContextProvider {

        @Override
        public RequestContext getContext() {
            return new TracingRequestContext(Context.current());
        }
    }
}
