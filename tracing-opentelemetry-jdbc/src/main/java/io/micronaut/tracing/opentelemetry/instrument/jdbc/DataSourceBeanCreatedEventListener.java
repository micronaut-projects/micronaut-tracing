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

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * Wraps the DataSource so OTEL can gather data for spans.
 * @param jdbcTelemetryConfiguration the otel JDBC configuration.
 */
@Singleton
@Internal
record DataSourceBeanCreatedEventListener(
    JdbcTelemetryConfiguration jdbcTelemetryConfiguration)
    implements BeanCreatedEventListener<DataSource>, Ordered {

    @Override
    public DataSource onCreated(@NonNull BeanCreatedEvent<DataSource> event) {
        return jdbcTelemetryConfiguration.builder
            .build().wrap(event.getBean());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
