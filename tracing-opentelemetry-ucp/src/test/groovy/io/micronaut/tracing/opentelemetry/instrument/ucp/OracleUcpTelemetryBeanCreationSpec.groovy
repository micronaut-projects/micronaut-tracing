package io.micronaut.tracing.opentelemetry.instrument.ucp

import io.micronaut.context.ApplicationContext
import io.micronaut.tracing.opentelemetry.instrument.ucp.fixture.TestUniversalConnectionPoolFactory
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import oracle.ucp.UniversalConnectionPool
import oracle.ucp.jdbc.PoolDataSource
import spock.lang.Specification

class OracleUcpTelemetryBeanCreationSpec extends Specification {

    private static final List<String> CONNECTION_COUNT_METRICS = [
            "db.client.connections.usage",
            "db.client.connection.count"
    ]
    private static final List<String> CONNECTION_MAX_METRICS = [
            "db.client.connections.max",
            "db.client.connection.max"
    ]
    private static final List<String> PENDING_REQUESTS_METRICS = [
            "db.client.connections.pending_requests",
            "db.client.connection.pending_requests"
    ]
    private static final List<String> POOL_NAME_ATTRIBUTES = [
            "pool.name",
            "db.client.connection.pool.name"
    ]
    private static final List<String> STATE_ATTRIBUTES = [
            "state",
            "db.client.connection.state"
    ]

    void "test Oracle UCP telemetry enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        def universalConnectionPoolBeanEventListener = ctx.getBean(UniversalConnectionPoolBeanEventListener)
        def oracleUcpTelemetryConfiguration = ctx.getBean(OracleUcpTelemetryConfiguration)

        then:
        universalConnectionPoolBeanEventListener
        oracleUcpTelemetryConfiguration

        cleanup:
        ctx.close()
    }

    void "test Oracle UCP telemetry registers metrics for final pool and unregisters on destroy"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        def reader = ctx.getBean(InMemoryMetricReader)
        def connectionPool = ctx.getBean(UniversalConnectionPool)

        expect:
        connectionPool.name == TestUniversalConnectionPoolFactory.FINAL_POOL_NAME

        when:
        def metrics = reader.collectAllMetrics()

        then:
        metricValue(metrics, CONNECTION_COUNT_METRICS, TestUniversalConnectionPoolFactory.FINAL_POOL_NAME, "used") == 5
        metricValue(metrics, CONNECTION_COUNT_METRICS, TestUniversalConnectionPoolFactory.FINAL_POOL_NAME, "idle") == 6
        metricValue(metrics, CONNECTION_MAX_METRICS, TestUniversalConnectionPoolFactory.FINAL_POOL_NAME, null) == 7
        metricValue(metrics, PENDING_REQUESTS_METRICS, TestUniversalConnectionPoolFactory.FINAL_POOL_NAME, null) == 8
        !hasMetricForPool(metrics, TestUniversalConnectionPoolFactory.ORIGINAL_POOL_NAME)

        when:
        ctx.destroyBean(connectionPool)

        then:
        !hasMetricForPool(reader.collectAllMetrics(), TestUniversalConnectionPoolFactory.FINAL_POOL_NAME)

        cleanup:
        ctx.close()
    }

    void "test Oracle UCP telemetry registers metrics for Micronaut UCP datasource"() {
        given:
        String poolName = "real-ucp-pool"
        ApplicationContext ctx = ApplicationContext.run([
                "datasources.default.connection-pool-name": poolName,
                "datasources.default.url": "jdbc:h2:mem:ucpSmoke;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
                "datasources.default.username": "sa",
                "datasources.default.connection-factory-class-name": "org.h2.jdbcx.JdbcDataSource",
                "datasources.default.initial-pool-size": 0,
                "datasources.default.min-pool-size": 0,
                "datasources.default.max-pool-size": 7,
        ])
        def reader = ctx.getBean(InMemoryMetricReader)
        def poolDataSource = ctx.getBean(PoolDataSource)

        expect:
        poolDataSource.getConnectionPoolName() == poolName

        when:
        def metrics = reader.collectAllMetrics()

        then:
        metricValue(metrics, CONNECTION_MAX_METRICS, poolName, null) == 7

        cleanup:
        ctx.close()
    }

    void "test Oracle UCP telemetry disabled with property"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder([
                "otel.instrumentation.ucp.enabled": "false",
        ]).singletons(TestUniversalConnectionPoolFactory.replacementConnectionPool()).start()

        when:
        def universalConnectionPoolBeanEventListener = ctx.findBean(UniversalConnectionPoolBeanEventListener)
        def oracleUcpTelemetryConfiguration = ctx.findBean(OracleUcpTelemetryConfiguration)
        def reader = ctx.getBean(InMemoryMetricReader)

        then:
        universalConnectionPoolBeanEventListener.isEmpty()
        oracleUcpTelemetryConfiguration.isEmpty()
        ctx.findBean(ManagedUniversalConnectionPoolMetricsBinder).isEmpty()
        ctx.getBean(UniversalConnectionPool)
        !hasUcpMetrics(reader.collectAllMetrics())

        cleanup:
        ctx.close()
    }

    private static Long metricValue(Collection<MetricData> metrics, List<String> metricNames, String poolName, String state) {
        metrics.collect { metric ->
            if (!metricNames.contains(metric.name)) {
                return null
            }
            metric.longSumData.points.find { point ->
                attribute(point.attributes, POOL_NAME_ATTRIBUTES) == poolName &&
                        (state == null || attribute(point.attributes, STATE_ATTRIBUTES) == state)
            }?.value
        }.find { it != null }
    }

    private static boolean hasUcpMetrics(Collection<MetricData> metrics) {
        metrics.any {
            CONNECTION_COUNT_METRICS.contains(it.name) ||
                    CONNECTION_MAX_METRICS.contains(it.name) ||
                    PENDING_REQUESTS_METRICS.contains(it.name)
        }
    }

    private static boolean hasMetricForPool(Collection<MetricData> metrics, String poolName) {
        metrics.any { metric ->
            metric.name.startsWith("db.client.connection") && metric.longSumData.points.any { point ->
                attribute(point.attributes, POOL_NAME_ATTRIBUTES) == poolName
            }
        }
    }

    private static String attribute(io.opentelemetry.api.common.Attributes attributes, List<String> names) {
        names.collect { attributes.get(AttributeKey.stringKey(it)) }.find { it != null }
    }
}
