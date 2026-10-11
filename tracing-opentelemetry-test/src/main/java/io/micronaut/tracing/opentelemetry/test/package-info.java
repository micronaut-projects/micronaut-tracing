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
/**
 * Test support for Micronaut OpenTelemetry: an in-memory span exporter and metric reader registered
 * with the OpenTelemetry SDK, plus {@link io.micronaut.tracing.opentelemetry.test.TestSpans} for
 * span assertions. Add the module to the test classpath only. Disable it with
 * {@code tracing.opentelemetry.test.enabled=false}.
 *
 * @since 8.4.0
 */
@Configuration
@Requires(property = OpenTelemetryTestFactory.ENABLED, notEquals = StringUtils.FALSE)
@NullMarked
package io.micronaut.tracing.opentelemetry.test;

import org.jspecify.annotations.NullMarked;
import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
