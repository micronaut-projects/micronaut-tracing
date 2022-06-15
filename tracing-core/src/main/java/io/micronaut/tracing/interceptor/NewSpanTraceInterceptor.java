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
package io.micronaut.tracing.interceptor;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.instrument.util.TracingObserver;
import io.micronaut.tracing.instrument.util.TracingPublisher;
import io.micronaut.tracing.instrument.util.TracingPublisherUtils;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Implements tracing logic for <code>ContinueSpan</code> and <code>NewSpan</code>
 * using the Open Tracing API.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
@InterceptorBean({ContinueSpan.class, NewSpan.class})
public class NewSpanTraceInterceptor extends AbstractTraceInterceptor {

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param tracer for span creation and propagation across arbitrary transports
     */
    public NewSpanTraceInterceptor(Tracer tracer) {
        super(tracer);
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRACE.getPosition();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {

        Span currentSpan = tracer.activeSpan();
        AnnotationValue<NewSpan> newSpan = context.getAnnotation(NewSpan.class);
        boolean isNew = newSpan != null;
        if (!isNew) {
            return context.proceed();
        }
        String operationName = newSpan.stringValue().orElse(null);

        Optional<String> hystrixCommand = context.stringValue(HYSTRIX_ANNOTATION);
        if (StringUtils.isEmpty(operationName)) {
            // try hystrix command name
            operationName = hystrixCommand.orElse(context.getMethodName());
        }
        Tracer.SpanBuilder builder = tracer.buildSpan(operationName);
        if (currentSpan != null) {
            builder.asChildOf(currentSpan);
        }

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER:
                    Publisher<?> publisher = interceptedMethod.interceptResultAsPublisher();
                    if (publisher instanceof TracingPublisher) {
                        return publisher;
                    }
                    return interceptedMethod.handleResult(
                        TracingPublisherUtils.createTracingPublisher(publisher, tracer, builder, new TracingObserver() {

                            @Override
                            public void doOnSubscribe(@NonNull Span span) {
                                populateTags(context, hystrixCommand, span);
                            }

                        })
                    );
                case COMPLETION_STAGE:
                    Span span = builder.start();
                    try (Scope ignored = tracer.scopeManager().activate(span)) {
                        populateTags(context, hystrixCommand, span);
                        try {
                            CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                            if (completionStage != null) {
                                completionStage = completionStage.whenComplete((o, throwable) -> {
                                    if (throwable != null) {
                                        logError(span, throwable);
                                    }
                                    span.finish();
                                });
                            }
                            return interceptedMethod.handleResult(completionStage);
                        } catch (RuntimeException e) {
                            logError(span, e);
                            throw e;
                        }
                    }
                case SYNCHRONOUS:
                    Span syncSpan = builder.start();
                    try (Scope scope = tracer.scopeManager().activate(syncSpan)) {
                        populateTags(context, hystrixCommand, syncSpan);
                        try {
                            return context.proceed();
                        } catch (RuntimeException e) {
                            logError(syncSpan, e);
                            throw e;
                        } finally {
                            syncSpan.finish();
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
