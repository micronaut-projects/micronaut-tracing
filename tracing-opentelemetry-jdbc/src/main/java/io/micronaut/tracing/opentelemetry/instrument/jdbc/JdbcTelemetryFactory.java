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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.opentelemetry.api.OpenTelemetry;
import io.micronaut.jdbc.DataSourceResolver;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * Jdbc telemetry factory.
 */
@Factory
@Internal
final class JdbcTelemetryFactory {

    /**
     * Wraps the DataSource so OTEL can gather data for spans.
     *
     * @param telemetry instance of {@link OpenTelemetry}
     * @param resolver the {@link DataSourceResolver}
     * @return even listener for @{@link DataSource} creation
     */
    @Singleton
    BeanCreatedEventListener<DataSource> otel(OpenTelemetry telemetry, DataSourceResolver resolver) {
        return event -> {
            DataSource dataSource = event.getBean();
            return JdbcTelemetry.create(telemetry).wrap(resolver.resolve(dataSource));
        };
    }

}