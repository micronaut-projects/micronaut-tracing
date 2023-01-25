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

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.instrument.util.TracingObserver;
import io.micronaut.tracing.instrument.util.TracingPublisher;
import io.micronaut.tracing.instrument.util.TracingPublisherUtils;
import io.opentracing.Span;
import io.opentracing.Tracer;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

/**
 * Implements tracing logic for {@code ContinueSpan} and {@code NewSpan}
 * using the Open Tracing API.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
@InterceptorBean(ContinueSpan.class)
public class ContinueSpanInterceptor extends AbstractTraceInterceptor {

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param tracer            for span creation and propagation across arbitrary transports
     * @param conversionService the {@code ConversionService} instance
     */
    public ContinueSpanInterceptor(Tracer tracer, ConversionService conversionService) {
        super(tracer, conversionService);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Span currentSpan = tracer.activeSpan();
        if (currentSpan == null) {
            return context.proceed();
        }
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER:
                    Publisher<?> publisher = interceptedMethod.interceptResultAsPublisher();
                    if (publisher instanceof TracingPublisher) {
                        return publisher;
                    }
                    return interceptedMethod.handleResult(
                        TracingPublisherUtils.createTracingPublisher(publisher, tracer, conversionService, new TracingObserver() {

                            @Override
                            public void doOnSubscribe(@NonNull Span span) {
                                tagArguments(span, context);
                            }

                        })
                    );
                case COMPLETION_STAGE:
                case SYNCHRONOUS:
                    tagArguments(currentSpan, context);
                    try {
                        return context.proceed();
                    } catch (RuntimeException e) {
                        logError(currentSpan, e);
                        throw e;
                    }
                default:
                    return interceptedMethod.unsupported();
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }
}
