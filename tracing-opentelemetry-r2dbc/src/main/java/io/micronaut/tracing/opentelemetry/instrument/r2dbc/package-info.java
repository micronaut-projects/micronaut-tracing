/*
 * Copyright 2017-2024 original authors
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
 * Contains configuration that provides integration with R2DBC OpenTelemetry.
 *
 * @since 7.1.0
 */
@Configuration
@Requires(classes = {OpenTelemetry.class, ConnectionFactory.class, R2dbcTelemetry.class})
package io.micronaut.tracing.opentelemetry.instrument.r2dbc;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.r2dbc.v1_0.R2dbcTelemetry;
import io.r2dbc.spi.ConnectionFactory;
