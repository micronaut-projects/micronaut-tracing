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
package io.micronaut.tracing.opentelemetry.instrument.jms;

import io.micronaut.core.annotation.Internal;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.pool.PooledObject;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;

/**
 * A {@link JMSConnectionPool} that hands out connections whose producers trace the messages they
 * send.
 *
 * <p>The pooling is left to the wrapped pool: this pool is created empty (it opens no connections
 * of its own) and delegates every operation, wrapping the connections it returns.</p>
 *
 * @since 8.4.0
 */
@Internal
final class TracingJMSConnectionPool extends JMSConnectionPool {

    private final JMSConnectionPool delegate;
    private final JmsTelemetry telemetry;

    TracingJMSConnectionPool(JMSConnectionPool delegate, JmsTelemetry telemetry) {
        super(delegate.getConnectionFactory(), 0, 0);
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    @Override
    public Connection createConnection() throws JMSException {
        return telemetry.wrap(delegate.createConnection());
    }

    @Override
    public Connection createConnection(String userName, String password) throws JMSException {
        return telemetry.wrap(delegate.createConnection(userName, password));
    }

    @Override
    public PooledObject<Connection> request(Object... args) {
        return delegate.request(args);
    }

    @Override
    public void release(PooledObject<Connection> pooledObject) {
        delegate.release(pooledObject);
    }

    @Override
    public JMSContext createContext() {
        return delegate.createContext();
    }

    @Override
    public JMSContext createContext(String userName, String password) {
        return delegate.createContext(userName, password);
    }

    @Override
    public JMSContext createContext(String userName, String password, int sessionMode) {
        return delegate.createContext(userName, password, sessionMode);
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        return delegate.createContext(sessionMode);
    }

    @Override
    public ConnectionFactory getConnectionFactory() {
        return delegate.getConnectionFactory();
    }

    @Override
    public String toString() {
        return "TracingJMSConnectionPool(" + delegate + ")";
    }
}
