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

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Span data of a {@code @NewSpan} or {@code @ContinueSpan} method computed at compile time by
 * {@code io.micronaut.tracing.processing.SpanMetadataVisitor}, so that the interceptors do not have to
 * derive it from the method at runtime.
 *
 * <p>Not meant to be used in application code. Methods compiled without the visitor do not carry it and
 * the interceptors compute the same values at runtime.</p>
 *
 * <p>Kotlin HTTP route methods with a mangled JVM name also carry it, with only the {@link #method()}
 * member, for the {@code code.function.name} attribute of the HTTP server spans.</p>
 *
 * @since 8.4.0
 */
@Internal
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface SpanMetadata {

    /**
     * Name of the {@link #method()} member.
     */
    String MEMBER_METHOD = "method";

    /**
     * Name of the {@link #tagIndexes()} member.
     */
    String MEMBER_TAG_INDEXES = "tagIndexes";

    /**
     * Name of the {@link #tagNames()} member.
     */
    String MEMBER_TAG_NAMES = "tagNames";

    /**
     * @return the method name used in the span name: the source name of the method, without the
     * Kotlin mangling suffix of a function with an inline class (or {@code kotlin.Result}) in its signature
     */
    String method();

    /**
     * @return the indexes of the {@code @SpanTag} parameters, in parameter order
     */
    int[] tagIndexes() default {};

    /**
     * @return the tag names of the {@code @SpanTag} parameters, in the order of {@link #tagIndexes()}
     */
    String[] tagNames() default {};
}
