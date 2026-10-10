/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.tracing.opentelemetry.processing;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes;

import java.util.List;

/**
 * Transforms OpenTelemetry {@link AddingSpanAttributes} into {@link ContinueSpan}: the
 * {@code @SpanAttribute} parameters of the method are added to the current span and no span is created.
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
public class AddingSpanAttributesAnnotationTransformer implements TypedAnnotationTransformer<AddingSpanAttributes> {

    @Override
    public Class<AddingSpanAttributes> annotationType() {
        return AddingSpanAttributes.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<AddingSpanAttributes> annotation, VisitorContext visitorContext) {
        AnnotationValue<InterceptorBinding> interceptBinding = AnnotationValue.builder(InterceptorBinding.class)
            .build();

        AnnotationValue<ContinueSpan> continueSpan = AnnotationValue.builder(ContinueSpan.class)
            .stereotype(interceptBinding)
            .build();

        return List.of(continueSpan);
    }
}
