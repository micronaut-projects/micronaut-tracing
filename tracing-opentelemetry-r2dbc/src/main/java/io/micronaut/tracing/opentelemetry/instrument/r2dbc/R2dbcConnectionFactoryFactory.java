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
package io.micronaut.tracing.opentelemetry.instrument.r2dbc;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.r2dbc.R2dbcConnectionFactoryBean;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * Produces instrumented R2DBC connection factories.
 */
@Factory
@Internal
@Requires(property = R2dbcTelemetryConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
class R2dbcConnectionFactoryFactory {

    @EachBean(ConnectionFactoryOptions.class)
    @Context
    @Replaces(bean = ConnectionFactory.class, factory = R2dbcConnectionFactoryBean.class)
    protected ConnectionFactory connectionFactory(ConnectionFactoryOptions options,
                                                  R2dbcTelemetryConfiguration r2dbcTelemetryConfiguration) {
        return r2dbcTelemetryConfiguration.builder
            .build()
            .wrapConnectionFactory(ConnectionFactories.get(options), options);
    }
}
