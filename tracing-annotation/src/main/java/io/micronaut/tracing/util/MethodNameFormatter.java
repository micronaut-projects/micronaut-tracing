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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats method names for tracing output.
 */
@Internal
public final class MethodNameFormatter {

    private static final Pattern KOTLIN_INLINE_CLASS_MANGLING = Pattern.compile("^(?<name>.+)-[^$]+(?:\\$default)?$");

    private MethodNameFormatter() {
    }

    /**
     * Normalize Kotlin inline class mangled method names to the source method name.
     *
     * @param methodName The raw method name
     * @return The formatted method name
     */
    public static String format(String methodName) {
        Matcher matcher = KOTLIN_INLINE_CLASS_MANGLING.matcher(methodName);
        if (matcher.matches()) {
            return matcher.group("name");
        }
        return methodName;
    }
}
