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
package io.micronaut.tracing.opentelemetry.utils;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.opentelemetry.context.Context;
import reactor.util.context.ContextView;

/**
 * Utils class to get the OpenTelemetry tracing context from the Reactor context.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Experimental
public final class OpenTelemetryReactorPropagation {

    private OpenTelemetryReactorPropagation() {
    }

    /**
     * Find the OpenTelemetry {@link Context} from the reactive context.
     * @param contextView The reactor's context view
     * @return The found context.
     */
    public static Context currentContext(ContextView contextView) {
        return ReactorPropagation.findContextElement(contextView, OpenTelemetryPropagationContext.class)
            .map(OpenTelemetryPropagationContext::context).orElseGet(Context::current);
    }

}
