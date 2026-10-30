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
package io.micronaut.tracing.opentracing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;

import java.util.List;

/**
 * The open tracing propagated context.
 *
 * @param tracer The tracer
 * @param span   The span
 * @author Denis Stepanov
 */
@Internal
public record OpenTracingPropagationContext(Tracer tracer,
                                            Span span) implements ThreadPropagatedContextElement<Scope> {

    /**
     * Creates a propagated context with the current OpenTracing span.
     *
     * @param context The propagated context
     * @param tracer  The tracer
     * @param span    The span
     * @return A propagated context with a single OpenTracing context element
     */
    @NonNull
    public static PropagatedContext withSpan(@NonNull PropagatedContext context,
                                             @NonNull Tracer tracer,
                                             @NonNull Span span) {
        OpenTracingPropagationContext element = new OpenTracingPropagationContext(tracer, span);
        PropagatedContext newContext = context;
        List<OpenTracingPropagationContext> existingContexts = context.findAll(OpenTracingPropagationContext.class).toList();
        for (OpenTracingPropagationContext existingContext : existingContexts) {
            newContext = newContext.minus(existingContext);
        }
        return newContext.plus(element);
    }

    @Override
    public Scope updateThreadContext() {
        return tracer.activateSpan(span);
    }

    @Override
    public void restoreThreadContext(Scope oldScope) {
        oldScope.close();
    }
}
