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
package io.micronaut.tracing.opentelemetry.processing;


import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.tracing.annotation.SpanTag;
import io.opentelemetry.extension.annotations.SpanAttribute;

import java.util.Collections;
import java.util.List;

/**
 * Adds {@link SpanTag} to {@link SpanAttribute}.
 *
 * @since 3.4.0
 * @author Nemanja Mikic
 */
public class SpanAttributeAnnotationMapper implements TypedAnnotationMapper<SpanAttribute> {

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<SpanAttribute> annotation, VisitorContext visitorContext) {

        return Collections.singletonList(AnnotationValue.builder(SpanTag.class)
            .value(annotation.stringValue().orElse(null))
            .build());
    }

    @Override
    public Class<SpanAttribute> annotationType() {
        return SpanAttribute.class;
    }
}
