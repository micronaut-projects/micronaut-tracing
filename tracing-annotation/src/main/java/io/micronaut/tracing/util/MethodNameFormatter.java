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
    /**
     * The Kotlin compiler mangles the names of methods with inline class parameters with a hash of the
     * signature: the first 5 bytes of its MD5 digest, URL-safe base64 encoded without padding, which is
     * always 7 characters.
     */
    private static final int MANGLING_HASH_LENGTH = 7;

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
        int manglingSeparator = name.length() - MANGLING_HASH_LENGTH - 1;
        if (manglingSeparator > 0 && name.charAt(manglingSeparator) == '-' && isKotlinManglingHash(name, manglingSeparator + 1)) {
            return name.substring(0, manglingSeparator);
        }
        return methodName;
    }

    private static boolean isKotlinManglingHash(String name, int hashStart) {
        for (int i = hashStart; i < name.length(); i++) {
            if (!isUrlSafeBase64Character(name.charAt(i))) {
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
