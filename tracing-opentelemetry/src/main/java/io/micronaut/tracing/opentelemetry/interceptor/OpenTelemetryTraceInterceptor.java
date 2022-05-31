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

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryObserver;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryPublisher;
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryPublisherUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;

/**
 * Implements tracing logic for <code>ContinueSpan</code> and <code>NewSpan</code>
 * using the Open Telemetry API.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
@Singleton
@Requires(beans = Tracer.class)
@InterceptorBean({ContinueSpan.class, NewSpan.class})
public class OpenTelemetryTraceInterceptor implements MethodInterceptor<Object, Object> {

    private final Instrumenter<ClassAndMethod, Object> instrumenter;

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param instrumenter the ClassAndMethod Instrumenter
     */
    public OpenTelemetryTraceInterceptor(@Named("micronautCodeTelemetryInstrumenter") Instrumenter<ClassAndMethod, Object> instrumenter) {
        this.instrumenter = instrumenter;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRACE.getPosition();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        boolean isContinue = context.hasAnnotation(ContinueSpan.class);
        AnnotationValue<NewSpan> newSpan = context.getAnnotation(NewSpan.class);
        boolean isNew = newSpan != null;
        if (!isContinue && !isNew) {
            return context.proceed();
        }

        Context currentContext = Context.current();

        if (isContinue) {
            InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
            try {
                switch (interceptedMethod.resultType()) {
                    case PUBLISHER:
                        try {
                            Publisher<?> publisher = interceptedMethod.interceptResultAsPublisher();
                            if (publisher instanceof OpenTelemetryPublisher) {
                                return publisher;
                            }
                            return interceptedMethod.handleResult(
                                OpenTelemetryPublisherUtils.createOpenTelemetryPublisher(publisher, null, currentContext, null, new OpenTelemetryObserver() {

                                    @Override
                                    public void doOnSubscribe(@NonNull Context openTelemetryContext) {
                                        tagArguments(context);
                                    }

                                }));
                        } catch (RuntimeException e) {
                                OpenTelemetryPublisherUtils.logError(currentContext, e);
                                throw e;
                        }
                    case COMPLETION_STAGE:
                    case SYNCHRONOUS:
                        tagArguments(context);
                        try {
                            return context.proceed();
                        } catch (RuntimeException e) {
                            OpenTelemetryPublisherUtils.logError(currentContext, e);
                            throw e;
                        }
                    default:
                        return interceptedMethod.unsupported();
                }
            } catch (Exception e) {
                return interceptedMethod.handleException(e);
            }
        } else {
            // must be new
            // don't create a nested span if you're not supposed to.
            String operationName = newSpan.stringValue().orElse("");
            ClassAndMethod classAndMethod = ClassAndMethod.create(context.getDeclaringType(), context.getMethodName());

            if (!StringUtils.isEmpty(operationName)) {
                classAndMethod = ClassAndMethod.create(classAndMethod.declaringClass(), classAndMethod.methodName() + '#' + operationName);
            }

            InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
            try {
                if (!instrumenter.shouldStart(currentContext, classAndMethod)) {
                    return context.proceed();
                }
                final Context newContext = instrumenter.start(currentContext, classAndMethod);

                switch (interceptedMethod.resultType()) {
                    case PUBLISHER:
                        try (Scope ignored = newContext.makeCurrent()) {
                            Publisher<?> publisher = interceptedMethod.interceptResultAsPublisher();
                            if (publisher instanceof OpenTelemetryPublisher) {
                                return publisher;
                            }
                            return interceptedMethod.handleResult(OpenTelemetryPublisherUtils.createOpenTelemetryPublisher(publisher, instrumenter, newContext, classAndMethod, new OpenTelemetryObserver() {
                                @Override
                                public void doOnSubscribe(@NonNull Context openTelemetryContext) {
                                    tagArguments(context);
                                }
                            }));
                        }  catch (RuntimeException e) {
                            OpenTelemetryPublisherUtils.logError(newContext, e);
                            instrumenter.end(newContext, classAndMethod, null, e);
                            throw e;
                        }
                    case COMPLETION_STAGE:
                        try (Scope ignored = newContext.makeCurrent()) {
                            tagArguments(context);
                            try {
                                CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                                if (completionStage != null) {
                                    ClassAndMethod finalClassAndMethod = classAndMethod;
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
                            } catch (RuntimeException e) {
                                OpenTelemetryPublisherUtils.logError(newContext, e);
                                instrumenter.end(newContext, classAndMethod, null, e);
                                throw e;
                            }
                        }
                    case SYNCHRONOUS:
                        try (Scope ignored = newContext.makeCurrent()) {
                            tagArguments(context);
                            try {
                                Object response = context.proceed();
                                instrumenter.end(newContext, classAndMethod, response, null);
                                return response;
                            } catch (RuntimeException e) {
                                OpenTelemetryPublisherUtils.logError(newContext, e);
                                instrumenter.end(newContext, classAndMethod, null, e);
                                throw e;
                            }
                        }
                    default:
                        return interceptedMethod.unsupported();
                }
            } catch (Exception e) {
                return interceptedMethod.handleException(e);
            }
        }
    }

    private void tagArguments(MethodInvocationContext<Object, Object> context) {
        Argument<?>[] arguments = context.getArguments();
        Object[] parameterValues = context.getParameterValues();
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
            if (annotationMetadata.hasAnnotation(SpanTag.class)) {
                Object v = parameterValues[i];
                if (v != null) {
                    String tagName = annotationMetadata.stringValue(SpanTag.class).orElse(argument.getName());
                    Span span = Span.current();
                    span.setAttribute(tagName, v.toString());
                }
            }
        }
    }
}
