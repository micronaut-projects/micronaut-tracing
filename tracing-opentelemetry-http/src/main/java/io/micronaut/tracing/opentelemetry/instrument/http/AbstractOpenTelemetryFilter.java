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
package io.micronaut.tracing.opentelemetry.instrument.http;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.filter.HttpFilter;
import io.opentelemetry.api.trace.Span;

import java.util.function.Predicate;

/**
 * Abstract filter used for Open Tracing based HTTP tracing.
 *
 * @author Nemanja Mikic
 */
public abstract class AbstractOpenTelemetryFilter implements HttpFilter {

    public static final String CLIENT_PATH = "${tracing.http.client.path:/**}";
    public static final String SERVER_PATH = "${tracing.http.server.path:/**}";
    public static final String TAG_ERROR = "error";

    @Nullable
    private final Predicate<String> pathExclusionTest;

    /**
     * Configure tracer in the filter for span creation and propagation across
     * arbitrary transports.
     *
     * @param pathExclusionTest the predicate for excluding URI paths from tracing
     */
    public AbstractOpenTelemetryFilter(@Nullable Predicate<String> pathExclusionTest) {
        this.pathExclusionTest = pathExclusionTest;
    }

    /**
     * Sets the error tags to use on the span.
     *
     * @param span  the span
     * @param error the error
     */
    protected void setErrorTags(Span span, Throwable error) {
        if (error == null) {
            return;
        }

        String message = error.getMessage();
        if (message == null) {
            message = error.getClass().getSimpleName();
        }
        span.setAttribute(TAG_ERROR, message);
    }

    /**
     * Tests if the defined path should be excluded from tracing.
     *
     * @param path the path to test
     * @return {@code true} if the path should be excluded
     */
    protected boolean shouldExclude(@Nullable String path) {
        return pathExclusionTest != null && path != null && pathExclusionTest.test(path);
    }
}
