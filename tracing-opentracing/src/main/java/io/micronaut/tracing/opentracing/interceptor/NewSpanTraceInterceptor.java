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
package io.micronaut.tracing.opentracing.interceptor;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.opentracing.OpenTracingPropagationContext;
import io.opentracing.Span;
import io.opentracing.Tracer;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

/**
 * Implements tracing logic for {@code ContinueSpan} and {@code NewSpan}
 * using the Open Tracing API.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Singleton
@Requires(beans = Tracer.class)
@InterceptorBean(value = NewSpan.class)
public final class NewSpanTraceInterceptor extends AbstractTraceInterceptor {

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param tracer            for span creation and propagation across arbitrary transports
     * @param conversionService the {@code ConversionService} instance
     */
    public NewSpanTraceInterceptor(Tracer tracer, ConversionService conversionService) {
        super(tracer, conversionService);
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {

        Span currentSpan = tracer.activeSpan();
        AnnotationValue<NewSpan> newSpan = context.getAnnotation(NewSpan.class);
        boolean isNew = newSpan != null;
        if (!isNew) {
            return context.proceed();
        }
        String operationName = newSpan.stringValue().orElse(context.getDeclaringType().getSimpleName() + "." + context.getMethodName());

        Tracer.SpanBuilder builder = tracer.buildSpan(operationName);
        if (currentSpan != null) {
            builder.asChildOf(currentSpan);
        }

        Span span = builder.start();
        populateTags(context, span);

        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new OpenTracingPropagationContext(tracer, span))
            .propagate()) {

            populateTags(context, span);

            InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
            try {
                switch (interceptedMethod.resultType()) {
                    case PUBLISHER -> {
                        return interceptedMethod.handleResult(
                            Mono.from(interceptedMethod.interceptResultAsPublisher())
                                .doOnError(throwable -> logError(span, throwable))
                                .doOnTerminate(span::finish)
                        );
                    }
                    case COMPLETION_STAGE -> {
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
                            span.finish();
                            throw e;
                        }
                    }
                    case SYNCHRONOUS -> {
                        try {
                            return context.proceed();
                        } catch (RuntimeException e) {
                            logError(span, e);
                            throw e;
                        } finally {
                            span.finish();
                        }
                    }
                    default -> {
                        return interceptedMethod.unsupported();
                    }
                }
            } catch (Exception e) {
                logError(span, e);
                span.finish();
                return interceptedMethod.handleException(e);
            }
        }
    }

}
