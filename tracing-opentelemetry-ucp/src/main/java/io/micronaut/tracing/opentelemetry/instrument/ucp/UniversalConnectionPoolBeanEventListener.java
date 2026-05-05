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
package io.micronaut.tracing.opentelemetry.instrument.ucp;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;
import oracle.ucp.UniversalConnectionPool;

/**
 * Registers the UniversalConnectionPool bean so OpenTelemetry can collect metrics.
 *
 * @param oracleUcpTelemetryConfiguration the Oracle UCP telemetry configuration
 * @author Andreas Brenk
 * @since 7.2.1
 */
@Singleton
@Internal
record UniversalConnectionPoolBeanEventListener(
    OracleUcpTelemetryConfiguration oracleUcpTelemetryConfiguration)
    implements BeanCreatedEventListener<UniversalConnectionPool>, BeanDestroyedEventListener<UniversalConnectionPool>, Ordered {

    @Override
    public UniversalConnectionPool onCreated(@NonNull BeanCreatedEvent<UniversalConnectionPool> event) {
        final UniversalConnectionPool connectionPool = event.getBean();
        oracleUcpTelemetryConfiguration.oracleUcpTelemetry.registerMetrics(connectionPool);

        return connectionPool;
    }

    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<UniversalConnectionPool> event) {
        final UniversalConnectionPool connectionPool = event.getBean();
        oracleUcpTelemetryConfiguration.oracleUcpTelemetry.unregisterMetrics(connectionPool);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
