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

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micronaut.core.annotation.Internal;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Starts the command spans of the OpenTelemetry {@link CommandListener} in the OpenTelemetry
 * {@link Context} carried by the {@link TracingRequestContext} of the command, if any. The reactive
 * streams driver notifies the listener on its own threads, where the context of the caller is not
 * current.
 *
 * @since 8.4.0
 */
@Internal
final class ContextPropagatingCommandListener implements CommandListener {

    private final CommandListener delegate;

    ContextPropagatingCommandListener(CommandListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        Context context = TracingRequestContext.otelContext(event.getRequestContext());
        if (context == null || context == Context.current()) {
            delegate.commandStarted(event);
            return;
        }
        try (Scope ignored = context.makeCurrent()) {
            delegate.commandStarted(event);
        }
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        delegate.commandSucceeded(event);
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        delegate.commandFailed(event);
    }
}
