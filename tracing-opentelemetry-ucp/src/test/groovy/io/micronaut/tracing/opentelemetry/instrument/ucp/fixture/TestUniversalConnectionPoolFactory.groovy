package io.micronaut.tracing.opentelemetry.instrument.ucp.fixture

import io.micronaut.context.annotation.Factory
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.order.Ordered
import jakarta.inject.Singleton
import oracle.ucp.UniversalConnectionPool
import oracle.ucp.UniversalConnectionPoolStatistics

@Factory
class TestUniversalConnectionPoolFactory {

    static final String ORIGINAL_POOL_NAME = "original-pool"
    static final String FINAL_POOL_NAME = "final-pool"

    @Singleton
    UniversalConnectionPool universalConnectionPool() {
        connectionPool(ORIGINAL_POOL_NAME, 1, 2, 3, 4)
    }

    static UniversalConnectionPool replacementConnectionPool() {
        connectionPool(FINAL_POOL_NAME, 5, 6, 7, 8)
    }

    private static UniversalConnectionPool connectionPool(String name, int borrowedConnections, int availableConnections,
                                                          int maxPoolSize, int pendingRequests) {
        UniversalConnectionPoolStatistics statistics = [
                getPeakConnectionsCount: { maxPoolSize },
                getPendingRequestsCount: { pendingRequests }
        ] as UniversalConnectionPoolStatistics

        [
                getName: { name },
                getBorrowedConnectionsCount: { borrowedConnections },
                getAvailableConnectionsCount: { availableConnections },
                getMaxPoolSize: { maxPoolSize },
                getStatistics: { statistics }
        ] as UniversalConnectionPool
    }
}

@Singleton
class ReplacingUniversalConnectionPoolListener implements BeanCreatedEventListener<UniversalConnectionPool>, Ordered {

    @Override
    UniversalConnectionPool onCreated(@NonNull BeanCreatedEvent<UniversalConnectionPool> event) {
        TestUniversalConnectionPoolFactory.replacementConnectionPool()
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE + 100
    }
}
