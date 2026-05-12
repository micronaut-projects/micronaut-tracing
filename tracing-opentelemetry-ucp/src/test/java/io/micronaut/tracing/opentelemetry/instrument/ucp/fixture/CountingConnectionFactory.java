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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Connection factory used to assert when UCP creates physical connections.
 */
public final class CountingConnectionFactory implements DataSource {

    private static final AtomicInteger CONNECTIONS_CREATED = new AtomicInteger();

    private String url;
    private String user;
    private String password;
    private PrintWriter logWriter;
    private int loginTimeout;

    /**
     * Resets the number of physical connections created by this factory.
     */
    public static void reset() {
        CONNECTIONS_CREATED.set(0);
    }

    /**
     * @return the number of physical connections created by this factory
     */
    public static int getConnectionsCreated() {
        return CONNECTIONS_CREATED.get();
    }

    /**
     * @param url the JDBC URL
     */
    public void setURL(String url) {
        this.url = url;
    }

    /**
     * @param user the JDBC user
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * @param password the JDBC password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        CONNECTIONS_CREATED.incrementAndGet();
        if (user == null) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        CONNECTIONS_CREATED.incrementAndGet();
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter logWriter) {
        this.logWriter = logWriter;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (isWrapperFor(iface)) {
            return iface.cast(this);
        }
        throw new SQLException("Unsupported unwrap type: " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
