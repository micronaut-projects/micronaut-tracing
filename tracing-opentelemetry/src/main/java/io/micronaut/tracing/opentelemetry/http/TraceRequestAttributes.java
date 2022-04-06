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
package io.micronaut.tracing.opentelemetry.http;

import io.micronaut.core.annotation.NonNull;

/**
 * Constants used to store <code>Span</code>s in instrumented request attributes.
 *
 * @author Nemanja Mikic
 * @since 1.0
 */
public enum TraceRequestAttributes implements CharSequence {

    /**
     * The attribute used to store the current span.
     */
    CURRENT_SPAN("micronaut.tracing.currentSpan"),

    /**
     * The attribute used to store the current scope.
     */
    CURRENT_SCOPE("micronaut.tracing.currentScope"),

    /**
     * The attribute used to store the current span context.
     */
    CURRENT_SPAN_CONTEXT("micronaut.tracing.currentSpanContext");

    private final String attribute;

    /**
     * @param attribute request attribute
     */
    TraceRequestAttributes(String attribute) {
        this.attribute = attribute;
    }

    @Override
    public int length() {
        return attribute.length();
    }

    @Override
    public char charAt(int index) {
        return attribute.charAt(index);
    }

    @NonNull
    @Override
    public CharSequence subSequence(int start, int end) {
        return attribute.subSequence(start, end);
    }

    @Override
    public String toString() {
        return attribute;
    }
}
