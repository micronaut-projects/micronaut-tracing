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
package io.micronaut.tracing.opentelemetry.test;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.context.TestContext;
import io.micronaut.test.context.TestExecutionListener;
import jakarta.inject.Singleton;

/**
 * Discards the captured spans before each test method of a {@code @MicronautTest} test (JUnit 5 or Spock),
 * so spans from one test method do not leak into the next one sharing the application context.
 * Disable it with {@value OpenTelemetryTestFactory#RESET_BEFORE_EACH}{@code =false}.
 *
 * @since 8.4.0
 */
@Internal
@Singleton
@Requires(classes = TestExecutionListener.class)
@Requires(property = OpenTelemetryTestFactory.RESET_BEFORE_EACH, notEquals = StringUtils.FALSE)
final class ResetSpansTestExecutionListener implements TestExecutionListener {

    private final TestSpans testSpans;

    ResetSpansTestExecutionListener(TestSpans testSpans) {
        this.testSpans = testSpans;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        testSpans.reset();
    }
}
