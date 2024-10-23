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

import io.micronaut.context.annotation.Replaces;
import io.micronaut.data.connection.jdbc.DelegatingDataSourceResolver;
import io.opentelemetry.instrumentation.jdbc.datasource.OpenTelemetryDataSource;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * The OpenTelemetryResolver replaces {@link DelegatingDataSourceResolver}.
 */
@Singleton
@Replaces(DelegatingDataSourceResolver.class)
public class OpenTelemetryResolver extends DelegatingDataSourceResolver {

    @Override
    public DataSource resolve(DataSource dataSource) {
        if (dataSource instanceof OpenTelemetryDataSource otel) {
            try {
                return otel.unwrap(DataSource.class);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return super.resolve(dataSource);
    }

}
