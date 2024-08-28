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

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletionStage;

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
@InterceptorBean(NewSpan.class)
public final class NewSpanOpenTelemetryTraceInterceptor extends AbstractOpenTelemetryTraceInterceptor {

    private final ConversionService conversionService;

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param instrumenter      The ClassAndMethod Instrumenter
     * @param conversionService The conversion service
     */
    public NewSpanOpenTelemetryTraceInterceptor(@Named("micronautCodeTelemetryInstrumenter") Instrumenter<ClassAndMethod, Object> instrumenter,
                                                ConversionService conversionService) {
        super(instrumenter);
        this.conversionService = conversionService;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        AnnotationValue<NewSpan> newSpan = context.getAnnotation(NewSpan.class);
        boolean isNew = newSpan != null;
        if (!isNew) {
            return context.proceed();
        }
        // must be new
        // don't create a nested span if you're not supposed to.
        String operationName = newSpan.stringValue().orElse("");
        ClassAndMethod classAndMethod;

        ClassAndMethod basicClassAndMethod = ClassAndMethod.create(context.getDeclaringType(), context.getMethodName());
        if (StringUtils.isNotEmpty(operationName)) {
            classAndMethod = ClassAndMethod.create(basicClassAndMethod.declaringClass(), basicClassAndMethod.methodName() + '#' + operationName);
        } else {
            classAndMethod = basicClassAndMethod;
        }

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);

        Context currentContext = Context.current();
        if (!instrumenter.shouldStart(currentContext, classAndMethod)) {
            return context.proceed();
        }

        final Context newContext = instrumenter.start(currentContext, classAndMethod);

        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(newContext))
            .propagate()) {

            tagArguments(context);

            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    return interceptedMethod.handleResult(
                        Flux.from(interceptedMethod.interceptResultAsPublisher())
                            .doOnNext(value -> instrumenter.end(newContext, classAndMethod, value, null))
                            .doOnComplete(() -> instrumenter.end(newContext, classAndMethod, null, null))
                            .doOnError(throwable -> instrumenter.end(newContext, classAndMethod, null, throwable))
                    );
                }
                case COMPLETION_STAGE -> {
                    CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                    if (completionStage != null) {
                        completionStage = completionStage.whenComplete((o, throwable) -> {
                            if (throwable != null) {
                                instrumenter.end(newContext, classAndMethod, null, throwable);
                            } else {
                                instrumenter.end(newContext, classAndMethod, o, null);
                            }
                        });
                    }
                    return interceptedMethod.handleResult(completionStage);
                }
                case SYNCHRONOUS -> {
                    Object response = context.proceed();
                    instrumenter.end(newContext, classAndMethod, response, null);
                    return response;
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            instrumenter.end(newContext, classAndMethod, null, e);
            return interceptedMethod.handleException(e);
        }
    }
}
