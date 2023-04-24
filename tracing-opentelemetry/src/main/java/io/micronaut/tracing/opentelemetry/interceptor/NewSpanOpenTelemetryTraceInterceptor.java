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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryPublisherUtils;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.util.ClassAndMethod;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletionStage;

/**
 * Implements tracing logic for {@code ContinueSpan} and {@code NewSpan}
 * using the Open Telemetry API.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Singleton
@Requires(beans = Tracer.class)
@InterceptorBean(NewSpan.class)
public class NewSpanOpenTelemetryTraceInterceptor extends AbstractOpenTelemetryTraceInterceptor {

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param instrumenter the ClassAndMethod Instrumenter
     */
    public NewSpanOpenTelemetryTraceInterceptor(@Named("micronautCodeTelemetryInstrumenter") Instrumenter<ClassAndMethod, Object> instrumenter) {
        super(instrumenter);
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        AnnotationValue<NewSpan> newSpan = context.getAnnotation(NewSpan.class);
        boolean isNew = newSpan != null;
        if (!isNew) {
            return context.proceed();
        }
        Context currentContext = Context.current();
        // must be new
        // don't create a nested span if you're not supposed to.
        String operationName = newSpan.stringValue().orElse("");
        ClassAndMethod classAndMethod = ClassAndMethod.create(context.getDeclaringType(), context.getMethodName());

        if (!StringUtils.isEmpty(operationName)) {
            classAndMethod = ClassAndMethod.create(classAndMethod.declaringClass(), classAndMethod.methodName() + '#' + operationName);
        }

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
        if (!instrumenter.shouldStart(currentContext, classAndMethod)) {
            return context.proceed();
        }
        final Context newContext = instrumenter.start(currentContext, classAndMethod);
        try (PropagatedContext.InContext ignore = PropagatedContext.getOrEmpty().plus(new OpenTelemetryPropagationContext(newContext)).propagate()) {
            try (Scope ignored = newContext.makeCurrent()) {
                tagArguments(context);
                ClassAndMethod finalClassAndMethod = classAndMethod;

                switch (interceptedMethod.resultType()) {
                    case PUBLISHER -> {
                        return interceptedMethod.handleResult(
                            Flux.from(interceptedMethod.interceptResultAsPublisher())
                                .doOnComplete(() -> instrumenter.end(newContext, finalClassAndMethod, null, null))
                                .doOnError(throwable -> {
                                    OpenTelemetryPublisherUtils.logError(newContext, throwable);
                                    instrumenter.end(newContext, finalClassAndMethod, null, throwable);
                                })
                        );
                    }
                    case COMPLETION_STAGE -> {
                        CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                        if (completionStage != null) {
                            completionStage = completionStage.whenComplete((o, throwable) -> {
                                if (throwable != null) {
                                    OpenTelemetryPublisherUtils.logError(newContext, throwable);
                                    instrumenter.end(newContext, finalClassAndMethod, null, throwable);
                                } else {
                                    instrumenter.end(newContext, finalClassAndMethod, o, null);
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
            } catch (RuntimeException e) {
                OpenTelemetryPublisherUtils.logError(newContext, e);
                instrumenter.end(newContext, classAndMethod, null, e);
                throw e;
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }
}
