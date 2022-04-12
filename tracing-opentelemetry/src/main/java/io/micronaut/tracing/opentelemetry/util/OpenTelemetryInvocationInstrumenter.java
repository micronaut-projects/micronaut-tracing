/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tracing.opentelemetry.util;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.inject.Singleton;

/**
 * Tracing invocation instrument for OpenTelemetry.
 *
 * @author Nemanja Mikic
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
@Requires(missingBeans = TracingInvocationInstrumenterFactory.class)
@Internal
public class OpenTelemetryInvocationInstrumenter implements TracingInvocationInstrumenterFactory, ReactiveInvocationInstrumenterFactory {


    protected OpenTelemetryInvocationInstrumenter() {
    }

    @Override
    public InvocationInstrumenter newReactiveInvocationInstrumenter() {
        return newTracingInvocationInstrumenter();
    }

    @Override
    public InvocationInstrumenter newTracingInvocationInstrumenter() {
        Span activeSpan = Span.current();
        if (activeSpan == null || !activeSpan.getSpanContext().isValid()) {
            return null;
        }

        return () -> {
            Scope activeScope = Context.current().with(activeSpan).makeCurrent();
            return cleanup -> activeScope.close();
        };
    }
}
