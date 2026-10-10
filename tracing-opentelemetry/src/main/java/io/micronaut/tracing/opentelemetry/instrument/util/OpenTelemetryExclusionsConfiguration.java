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
package io.micronaut.tracing.opentelemetry.instrument.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.micronaut.context.annotation.ConfigurationProperties;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.CollectionUtils;

/**
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@ConfigurationProperties(OpenTelemetryExclusionsConfiguration.PREFIX)
public class OpenTelemetryExclusionsConfiguration {

    public static final String PREFIX = "otel";

    /**
     * The constructs that change meaning once a pattern is wrapped in a group of an alternation: a back
     * reference ({@code \1}, {@code \k<name>}) refers to a group by a number or name that would change,
     * a quote ({@code \Q}) left open, or a comment of the {@code x} flag, would swallow the closing
     * parenthesis. Patterns using them are tested one by one.
     */
    private static final Pattern NOT_COMBINABLE = Pattern.compile("\\\\(?:[1-9]|k<|Q)|\\(\\?[a-zA-Z-]*x");

    private @Nullable List<String> exclusions;

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
     * @param exclusions regex patterns to be excluded if the request URI matches
     *
     * @see Pattern#compile(String)
     */
    public void setExclusions(@Nullable List<String> exclusions) {
        this.exclusions = exclusions;
    }

    /**
     * The patterns are compiled into a single alternation ({@code (?:p1)|(?:p2)|...}), so a path is
     * tested with one match instead of one per pattern. A path is excluded when it matches one of the
     * patterns entirely, as with {@link java.util.regex.Matcher#matches()} for each of them.
     *
     * @return null (implying everything should be included), or a Predicate
     *         which, when given a URL path, returns whether that path should
     *         be excluded from tracing.
     */
    @Nullable
    public Predicate<String> exclusionTest() {
        return compile(exclusions);
    }

    /**
     * Returns the exclusion test of the configured patterns and of additional patterns, compiled together
     * as for {@link #exclusionTest()}.
     *
     * @param additionalExclusions The additional patterns, for example the paths of the management endpoints
     * @return null (implying everything should be included), or a Predicate
     *         which, when given a URL path, returns whether that path should
     *         be excluded from tracing.
     * @since 8.4.0
     */
    @Nullable
    public Predicate<String> exclusionTest(@Nullable Collection<String> additionalExclusions) {
        if (CollectionUtils.isEmpty(additionalExclusions)) {
            return compile(exclusions);
        }
        var all = new ArrayList<String>();
        if (exclusions != null) {
            all.addAll(exclusions);
        }
        all.addAll(additionalExclusions);
        return compile(all);
    }

    @Nullable
    private static Predicate<String> compile(@Nullable List<String> exclusions) {
        if (CollectionUtils.isEmpty(exclusions)) {
            return null;
        }
        Pattern pattern = combine(exclusions);
        if (pattern != null) {
            return uri -> pattern.matcher(uri).matches();
        }
        Pattern[] patterns = exclusions.stream()
            .map(Pattern::compile)
            .toArray(Pattern[]::new);
        return uri -> {
            for (Pattern p : patterns) {
                if (p.matcher(uri).matches()) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * @param exclusions The patterns
     * @return The alternation of the patterns, or {@code null} if they cannot be combined
     */
    @Nullable
    private static Pattern combine(List<String> exclusions) {
        if (exclusions.size() == 1) {
            return Pattern.compile(exclusions.get(0));
        }
        var alternation = new StringBuilder();
        for (String exclusion : exclusions) {
            // compile each pattern on its own first, so an invalid one fails as before
            Pattern.compile(exclusion);
            if (NOT_COMBINABLE.matcher(exclusion).find()) {
                return null;
            }
            if (!alternation.isEmpty()) {
                alternation.append('|');
            }
            alternation.append("(?:").append(exclusion).append(')');
        }
        try {
            return Pattern.compile(alternation.toString());
        } catch (PatternSyntaxException e) {
            // e.g. the same named group in two patterns
            return null;
        }
    }
}
