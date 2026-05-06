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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.jdbc.PoolDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registers UCP pools created by Micronaut JDBC UCP so OpenTelemetry can collect metrics.
 */
@Context
@Singleton
@Internal
@Requires(beans = {UniversalConnectionPoolManager.class, DataSource.class})
final class ManagedUniversalConnectionPoolMetricsBinder {

    private final UniversalConnectionPoolMetricsRegistry universalConnectionPoolMetricsRegistry;
    private final DataSourceResolver dataSourceResolver;
    private final List<UniversalConnectionPool> registeredPools = new ArrayList<>();
    private final Set<String> registeredPoolNames = new HashSet<>();

    ManagedUniversalConnectionPoolMetricsBinder(
        UniversalConnectionPoolMetricsRegistry universalConnectionPoolMetricsRegistry,
        UniversalConnectionPoolManager connectionPoolManager,
        @Nullable DataSourceResolver dataSourceResolver,
        Collection<DataSource> dataSources) {
        this.universalConnectionPoolMetricsRegistry = universalConnectionPoolMetricsRegistry;
        this.dataSourceResolver = dataSourceResolver == null ? DataSourceResolver.DEFAULT : dataSourceResolver;
        try {
            for (DataSource dataSource : dataSources) {
                register(connectionPoolManager, dataSource);
            }
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    private void register(UniversalConnectionPoolManager connectionPoolManager, DataSource dataSource) {
        DataSource resolvedDataSource = dataSourceResolver.resolve(dataSource);
        if (resolvedDataSource instanceof PoolDataSource poolDataSource) {
            register(connectionPoolManager, poolDataSource);
        } else {
            try {
                if (resolvedDataSource.isWrapperFor(PoolDataSource.class)) {
                    register(connectionPoolManager, resolvedDataSource.unwrap(PoolDataSource.class));
                }
            } catch (SQLException e) {
                throw new ConfigurationException("Failed to unwrap PoolDataSource from DataSource bean", e);
            }
        }
    }

    private void register(UniversalConnectionPoolManager connectionPoolManager, PoolDataSource poolDataSource) {
        String poolName = poolDataSource.getConnectionPoolName();
        if (!registeredPoolNames.add(poolName)) {
            return;
        }
        try {
            UniversalConnectionPool connectionPool = connectionPoolManager.getConnectionPool(poolName);
            universalConnectionPoolMetricsRegistry.register(connectionPool);
            registeredPools.add(connectionPool);
        } catch (UniversalConnectionPoolException e) {
            throw new ConfigurationException(String.format("Failed to register metrics for UCP connection pool named: %s", poolName), e);
        }
    }

    /**
     * Unregisters metrics for managed UCP pools.
     */
    @PreDestroy
    void close() {
        for (UniversalConnectionPool connectionPool : registeredPools) {
            universalConnectionPoolMetricsRegistry.unregister(connectionPool);
        }
        registeredPools.clear();
        registeredPoolNames.clear();
    }
}
