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
package io.micronaut.tracing.opentelemetry.instrument.jdbc;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;

/**
 * The configuration class for jdbc telemetry.
 * @param enabled is jdbc telemetry enabled.
 * @param dataSourceInstrumenterEnabled sets {@link io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder#setDataSourceInstrumenterEnabled(boolean)}
 * @param statementInstrumenterEnabled sets {@link io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder#setStatementInstrumenterEnabled(boolean)}
 * @param statementSanitizationEnabled sets {@link io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder#setStatementSanitizationEnabled(boolean)}
 */
@Requires(property = JdbcTelemetryConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@ConfigurationProperties(JdbcTelemetryConfiguration.PREFIX)
record JdbcTelemetryConfiguration(@Nullable Boolean enabled, @Nullable Boolean dataSourceInstrumenterEnabled, @Nullable Boolean statementInstrumenterEnabled, @Nullable Boolean statementSanitizationEnabled) {
    public static final String PREFIX = "otel.instrumentation.jdbc";
}
