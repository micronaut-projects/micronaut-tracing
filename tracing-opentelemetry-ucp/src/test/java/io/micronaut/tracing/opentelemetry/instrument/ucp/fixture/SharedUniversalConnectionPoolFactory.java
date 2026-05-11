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
package io.micronaut.tracing.opentelemetry.instrument.ucp.fixture;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.jdbc.PoolDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Exposes the Micronaut datasource-managed UCP pool as a bean for duplicate-registration tests.
 */
@Factory
@Requires(property = "test.ucp.shared-pool.enabled", value = StringUtils.TRUE)
public final class SharedUniversalConnectionPoolFactory {

    /**
     * @param connectionPoolManager the UCP connection pool manager
     * @param dataSource            the configured datasource
     * @return the datasource-managed connection pool
     * @throws UniversalConnectionPoolException if the UCP pool cannot be found
     * @throws SQLException                     if the datasource cannot be unwrapped
     */
    @Singleton
    @Named("sharedConnectionPool")
    UniversalConnectionPool sharedConnectionPool(
        UniversalConnectionPoolManager connectionPoolManager,
        DataSource dataSource) throws UniversalConnectionPoolException, SQLException {
        PoolDataSource poolDataSource = dataSource.unwrap(PoolDataSource.class);
        return connectionPoolManager.getConnectionPool(poolDataSource.getConnectionPoolName());
    }

    /**
     * @param connectionPoolManager the UCP connection pool manager
     * @param poolName              the connection pool name
     * @return true if the UCP manager has a connection pool with the supplied name
     */
    public static boolean hasConnectionPool(UniversalConnectionPoolManager connectionPoolManager, String poolName) {
        try {
            return connectionPoolManager.getConnectionPool(poolName) != null;
        } catch (UniversalConnectionPoolException e) {
            return false;
        }
    }
}
