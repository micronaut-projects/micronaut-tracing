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

import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import oracle.ucp.UniversalConnectionPool;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Tracks registered UCP metrics across the Micronaut datasource and bean lifecycle paths.
 */
@Singleton
@Internal
final class UniversalConnectionPoolMetricsRegistry {

    private final OracleUcpTelemetryConfiguration oracleUcpTelemetryConfiguration;
    private final Map<UniversalConnectionPool, Registration> registrations = new IdentityHashMap<>();

    UniversalConnectionPoolMetricsRegistry(OracleUcpTelemetryConfiguration oracleUcpTelemetryConfiguration) {
        this.oracleUcpTelemetryConfiguration = oracleUcpTelemetryConfiguration;
    }

    synchronized void register(UniversalConnectionPool connectionPool) {
        Registration registration = registrations.get(connectionPool);
        if (registration == null) {
            oracleUcpTelemetryConfiguration.oracleUcpTelemetry.registerMetrics(connectionPool);
            registrations.put(connectionPool, new Registration());
        } else {
            registration.retain();
        }
    }

    synchronized void unregister(UniversalConnectionPool connectionPool) {
        Registration registration = registrations.get(connectionPool);
        if (registration == null) {
            return;
        }
        if (registration.release()) {
            oracleUcpTelemetryConfiguration.oracleUcpTelemetry.unregisterMetrics(connectionPool);
            registrations.remove(connectionPool);
        }
    }

    private static final class Registration {

        private int references = 1;

        private void retain() {
            references++;
        }

        private boolean release() {
            references--;
            return references == 0;
        }
    }
}
