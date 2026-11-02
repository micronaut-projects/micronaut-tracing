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
package io.micronaut.tracing.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * A plain singleton (not {@code @Context}) DataSource, so a test can control when the
 * DataSource is created relative to the rest of the context.
 */
@Factory
@Requires(property = LazyDataSourceFactory.URL_PROPERTY)
public class LazyDataSourceFactory {

    public static final String URL_PROPERTY = "test.lazy-datasource.url";

    /**
     * @param url the JDBC URL
     * @return a DataSource created on first lookup
     */
    @Singleton
    DataSource lazyDataSource(@Property(name = URL_PROPERTY) String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        return new HikariDataSource(config);
    }
}
