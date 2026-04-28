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

/**
 * Formats method names for tracing output.
 */
@Internal
public final class MethodNameFormatter {

    private static final String DEFAULT_METHOD_SUFFIX = "$default";
    private static final int MINIMUM_MANGLING_SUFFIX_LENGTH = 7;

    private MethodNameFormatter() {
    }

    /**
     * Normalize Kotlin inline class mangled method names to the source method name.
     *
     * @param methodName The raw method name
     * @return The formatted method name
     */
    public static String format(String methodName) {
        String name = methodName.endsWith(DEFAULT_METHOD_SUFFIX)
            ? methodName.substring(0, methodName.length() - DEFAULT_METHOD_SUFFIX.length())
            : methodName;
        int manglingSeparator = name.indexOf('-');
        if (manglingSeparator > 0 && isKotlinManglingSuffix(name, manglingSeparator + 1)) {
            return name.substring(0, manglingSeparator);
        }
        return methodName;
    }

    private static boolean isKotlinManglingSuffix(String name, int suffixStart) {
        if (name.length() - suffixStart < MINIMUM_MANGLING_SUFFIX_LENGTH) {
            return false;
        }
        for (int i = suffixStart; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isUrlSafeBase64Character(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUrlSafeBase64Character(char c) {
        return c >= 'A' && c <= 'Z'
            || c >= 'a' && c <= 'z'
            || c >= '0' && c <= '9'
            || c == '_'
            || c == '-';
    }
}
