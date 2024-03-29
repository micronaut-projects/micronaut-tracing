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
package io.micronaut.tracing.opentracing.instrument.http;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;

/**
 * @since 4.0
 */
@ConfigurationProperties(TracingExclusionsConfiguration.PREFIX)
public class TracingExclusionsConfiguration {

    public static final String PREFIX = "tracing";

    private List<String> exclusions;

    /**
     * @return the URI patterns to exclude from the tracing
     */
    @Nullable
    public List<String> getExclusions() {
        return exclusions;
    }

    /**
     * Sets the URI patterns to be excluded from tracing.
     *
     * @param exclusions regular expression patterns to be excluded from
     *                   tracing if the request URI matches
     *
     * @see Pattern#compile(String)
     */
    public void setExclusions(@Nullable List<String> exclusions) {
        this.exclusions = exclusions;
    }

    /**
     * @return null (implying everything should be included), or a Predicate
     *         which, when given a URL path, returns whether that path should
     *         be excluded from tracing.
     */
    @Nullable
    public Predicate<String> exclusionTest() {
        if (CollectionUtils.isEmpty(exclusions)) {
            return null;
        }

        List<Pattern> patterns = exclusions.stream()
            .map(Pattern::compile)
            .toList();
        return uri -> patterns.stream().anyMatch(pattern -> pattern.matcher(uri).matches());
    }
}
