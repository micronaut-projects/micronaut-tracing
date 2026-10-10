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
 * Developer tooling for Micronaut OpenTelemetry: a bounded in-memory store of the most recent completed
 * traces, read through {@link io.micronaut.tracing.opentelemetry.inspector.TraceInspector}.
 *
 * <p>Active when {@code tracing.opentelemetry.inspector.enabled} is {@code true}. When the property is not
 * set, it is active only in the {@code dev} environment. See {@link TraceInspectorEnabledCondition}.</p>
 *
 * @since 8.4.0
 */
@Configuration
@Requires(condition = TraceInspectorEnabledCondition.class)
package io.micronaut.tracing.opentelemetry.inspector;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
