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
 * OpenTelemetry Zipkin span exporter that sends spans with the Micronaut HTTP client.
 *
 * <p>Deprecated: OpenTelemetry stopped publishing {@code opentelemetry-exporter-zipkin} after 1.64.0,
 * so this module is pinned to that version and scheduled for removal in a future major release.
 * Use the OTLP exporter instead; Zipkin can receive OTLP data.</p>
 */
@NullMarked
package io.micronaut.tracing.opentelemetry.exporter.zipkin;

import org.jspecify.annotations.NullMarked;
