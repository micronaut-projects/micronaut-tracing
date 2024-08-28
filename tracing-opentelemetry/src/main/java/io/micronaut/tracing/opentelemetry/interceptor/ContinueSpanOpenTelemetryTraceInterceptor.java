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
package io.micronaut.tracing.opentelemetry.interceptor;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Implements tracing logic for {@code ContinueSpan} and {@code NewSpan}
 * using the Open Telemetry API.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Internal
@Singleton
@Requires(beans = Tracer.class)
@InterceptorBean(ContinueSpan.class)
public final class ContinueSpanOpenTelemetryTraceInterceptor extends AbstractOpenTelemetryTraceInterceptor {

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param instrumenter the ClassAndMethod Instrumenter
     */
    public ContinueSpanOpenTelemetryTraceInterceptor(@Named("micronautCodeTelemetryInstrumenter") Instrumenter<ClassAndMethod, Object> instrumenter) {
        super(instrumenter);
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Context currentContext = Context.current();

        if (currentContext.toString().equals("{}")) {
            return context.proceed();
        }
        tagArguments(context);

        return context.proceed();
    }
}
