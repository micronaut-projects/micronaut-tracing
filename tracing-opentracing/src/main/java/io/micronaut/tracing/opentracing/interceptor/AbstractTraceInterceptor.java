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

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.tracing.annotation.SpanTag;
import io.opentracing.Span;
import io.opentracing.Tracer;

import java.util.Map;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static io.opentracing.log.Fields.MESSAGE;

/**
 * Implements tracing logic for {@code ContinueSpan} and {@code NewSpan}
 * using the Open Tracing API.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Requires(beans = Tracer.class)
public abstract sealed class AbstractTraceInterceptor implements MethodInterceptor<Object, Object>
    permits ContinueSpanInterceptor, NewSpanTraceInterceptor {

    public static final String CLASS_TAG = "class";
    public static final String METHOD_TAG = "method";

    protected final Tracer tracer;

    protected final ConversionService conversionService;

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param tracer            for span creation and propagation across arbitrary transports
     * @param conversionService the {@code ConversionService} instance
     */
    protected AbstractTraceInterceptor(Tracer tracer, ConversionService conversionService) {
        this.tracer = tracer;
        this.conversionService = conversionService;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRACE.getPosition();
    }

    protected final void populateTags(MethodInvocationContext<Object, Object> context,
                                      Span span) {
        span.setTag(CLASS_TAG, context.getDeclaringType().getSimpleName());
        span.setTag(METHOD_TAG, context.getMethodName());
        tagArguments(span, context);
    }

    /**
     * Logs an error to the span.
     *
     * @param span the span
     * @param e    the error
     */
    public static void logError(Span span, Throwable e) {
        Map<String, Object> fields = CollectionUtils.newHashMap(2);
        fields.put(ERROR_OBJECT, e);
        String message = e.getMessage();
        if (message != null) {
            fields.put(MESSAGE, message);
        }
        span.log(fields);
    }

    protected final void tagArguments(Span span, MethodInvocationContext<Object, Object> context) {
        Argument<?>[] arguments = context.getArguments();
        Object[] parameterValues = context.getParameterValues();
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
            if (annotationMetadata.hasAnnotation(SpanTag.class)) {
                Object v = parameterValues[i];
                if (v != null) {
                    String tagName = annotationMetadata.stringValue(SpanTag.class).orElse(argument.getName());
                    span.setTag(tagName, v.toString());
                }
            }
        }
    }
}
