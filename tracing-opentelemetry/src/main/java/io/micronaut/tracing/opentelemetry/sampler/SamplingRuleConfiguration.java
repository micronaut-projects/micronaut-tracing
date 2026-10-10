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
package io.micronaut.tracing.opentelemetry.sampler;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;

/**
 * A sampling rule of the root server spans, configured as an element of the
 * {@code tracing.opentelemetry.sampler.rules} list. The first rule whose {@link #getPath() path} and
 * {@link #getRoute() route} patterns match a new server trace decides its sampling with its
 * {@link #getRatio() ratio}.
 *
 * @since 8.4.0
 */
@EachProperty(value = SamplingRuleConfiguration.PREFIX, list = true)
public class SamplingRuleConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = SamplerConfiguration.PREFIX + ".rules";

    private final int index;
    @Nullable
    private String path;
    @Nullable
    private String route;
    private double ratio;

    /**
     * @param index The position of the rule in the list
     */
    public SamplingRuleConfiguration(@Parameter Integer index) {
        this.index = index;
    }

    /**
     * @return the position of the rule in the list
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return the regular expression matched against the whole {@code url.path} of the request
     */
    @Nullable
    public String getPath() {
        return path;
    }

    /**
     * Sets the regular expression matched against the whole request path (the {@code url.path} attribute),
     * for example {@code /internal/.*}.
     *
     * @param path the path pattern
     */
    public void setPath(@Nullable String path) {
        this.path = path;
    }

    /**
     * @return the regular expression matched against the whole {@code http.route} of the request
     */
    @Nullable
    public String getRoute() {
        return route;
    }

    /**
     * Sets the regular expression matched against the whole route template of the request (the
     * {@code http.route} attribute), for example {@code /books/\{id\}} (braces are escaped in a regular expression). A request without a route (no matching
     * route) does not match.
     *
     * @param route the route pattern
     */
    public void setRoute(@Nullable String route) {
        this.route = route;
    }

    /**
     * @return the ratio of the matching traces that are sampled
     */
    public double getRatio() {
        return ratio;
    }

    /**
     * Sets the ratio of the matching traces that are sampled, between {@code 0} and {@code 1}, using the
     * trace id as {@code traceidratio} does. Defaults to {@code 0}: the matching traces are dropped.
     *
     * @param ratio the ratio
     */
    public void setRatio(double ratio) {
        this.ratio = ratio;
    }
}
