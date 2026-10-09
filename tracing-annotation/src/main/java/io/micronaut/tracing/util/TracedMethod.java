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
package io.micronaut.tracing.util;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;

import java.util.ArrayList;
import java.util.List;

/**
 * The span data of a traced method, resolved once per method by the tracing interceptors: from the
 * {@link SpanMetadata} computed at compile time when present, otherwise from the method at runtime.
 *
 * @since 8.4.0
 */
@Internal
public final class TracedMethod {

    private static final int[] NO_INDEXES = new int[0];
    private static final String[] NO_NAMES = new String[0];

    private final boolean newSpan;
    @Nullable
    private final String newSpanValue;
    private final String methodName;
    private final int[] tagIndexes;
    private final String[] tagNames;
    private final boolean precomputed;

    private TracedMethod(boolean newSpan,
                         @Nullable String newSpanValue,
                         String methodName,
                         int[] tagIndexes,
                         String[] tagNames,
                         boolean precomputed) {
        this.newSpan = newSpan;
        this.newSpanValue = newSpanValue;
        this.methodName = methodName;
        this.tagIndexes = tagIndexes;
        this.tagNames = tagNames;
        this.precomputed = precomputed;
    }

    /**
     * Resolves the span data of a method.
     *
     * @param method the method (or the invocation context of the method)
     * @return the span data
     */
    public static TracedMethod of(ExecutableMethod<?, ?> method) {
        AnnotationValue<NewSpan> newSpan = method.getAnnotation(NewSpan.class);
        String newSpanValue = newSpan == null ? null : newSpan.stringValue().filter(StringUtils::isNotEmpty).orElse(null);
        AnnotationValue<SpanMetadata> metadata = method.getAnnotation(SpanMetadata.class);
        if (metadata != null) {
            String methodName = metadata.stringValue(SpanMetadata.MEMBER_METHOD).orElse(null);
            int[] tagIndexes = metadata.intValues(SpanMetadata.MEMBER_TAG_INDEXES);
            String[] tagNames = metadata.stringValues(SpanMetadata.MEMBER_TAG_NAMES);
            if (methodName != null && tagIndexes.length == tagNames.length) {
                return new TracedMethod(newSpan != null, newSpanValue, methodName, tagIndexes, tagNames, true);
            }
        }
        return runtime(method, newSpan != null, newSpanValue);
    }

    private static TracedMethod runtime(ExecutableMethod<?, ?> method, boolean newSpan, @Nullable String newSpanValue) {
        Argument<?>[] arguments = method.getArguments();
        List<Integer> indexes = null;
        List<String> names = null;
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
            if (annotationMetadata.hasAnnotation(SpanTag.class)) {
                if (indexes == null) {
                    indexes = new ArrayList<>(arguments.length);
                    names = new ArrayList<>(arguments.length);
                }
                indexes.add(i);
                names.add(annotationMetadata.stringValue(SpanTag.class).filter(StringUtils::isNotEmpty).orElse(argument.getName()));
            }
        }
        int[] tagIndexes = NO_INDEXES;
        String[] tagNames = NO_NAMES;
        if (indexes != null) {
            tagIndexes = indexes.stream().mapToInt(Integer::intValue).toArray();
            tagNames = names.toArray(NO_NAMES);
        }
        return new TracedMethod(newSpan, newSpanValue, MethodNameFormatter.format(method.getMethodName()), tagIndexes, tagNames, false);
    }

    /**
     * @return whether the method is annotated with {@link NewSpan}
     */
    public boolean isNewSpan() {
        return newSpan;
    }

    /**
     * @return the non-empty {@link NewSpan#value()} or {@code null}
     */
    @Nullable
    public String getNewSpanValue() {
        return newSpanValue;
    }

    /**
     * @return the method name used for the span, without the Kotlin mangling suffix
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * @return the indexes of the {@link SpanTag} parameters, do not modify
     */
    public int[] getTagIndexes() {
        return tagIndexes;
    }

    /**
     * @return the tag names of the {@link SpanTag} parameters, in the order of {@link #getTagIndexes()}, do not modify
     */
    public String[] getTagNames() {
        return tagNames;
    }

    /**
     * @return whether the data was computed at compile time
     */
    public boolean isPrecomputed() {
        return precomputed;
    }
}
