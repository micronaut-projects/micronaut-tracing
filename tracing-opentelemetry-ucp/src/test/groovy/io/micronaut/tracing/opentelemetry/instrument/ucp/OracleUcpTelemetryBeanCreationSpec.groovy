package io.micronaut.tracing.opentelemetry.instrument.ucp

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.tracing.opentelemetry.instrument.ucp.fixture.CountingConnectionFactory
import io.micronaut.tracing.opentelemetry.instrument.ucp.fixture.SharedUniversalConnectionPoolFactory
import io.micronaut.tracing.opentelemetry.instrument.ucp.fixture.TestUniversalConnectionPoolFactory
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import oracle.ucp.UniversalConnectionPool
import oracle.ucp.UniversalConnectionPoolAdapter
import oracle.ucp.UniversalConnectionPoolException
import oracle.ucp.admin.UniversalConnectionPoolManager
import oracle.ucp.jdbc.PoolDataSource
import oracle.ucp.jdbc.PoolDataSourceFactory
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.SQLException

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

    void "test Oracle UCP telemetry unregisters metrics when context closes"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        def reader = ctx.getBean(InMemoryMetricReader)
        def connectionPool = ctx.getBean(UniversalConnectionPool)

        expect:
        connectionPool.name == TestUniversalConnectionPoolFactory.FINAL_POOL_NAME
        hasMetricForPool(reader.collectAllMetrics(), TestUniversalConnectionPoolFactory.FINAL_POOL_NAME)

        when:
        ctx.close()

        then:
        !hasMetricForPool(reader.collectAllMetrics(), TestUniversalConnectionPoolFactory.FINAL_POOL_NAME)

        cleanup:
        ctx.close()
    }

    void "test Oracle UCP telemetry registers metrics for Micronaut UCP datasource"() {
        given:
        String poolName = "real-ucp-pool"
        ApplicationContext ctx = ApplicationContext.run(ucpDataSourceConfiguration(poolName, "ucpSmoke"))
        def reader = ctx.getBean(InMemoryMetricReader)
        def poolDataSource = ctx.getBean(DataSource).unwrap(PoolDataSource)

        expect:
        poolDataSource.getConnectionPoolName() == poolName

        when:
        def connection = poolDataSource.connection
        def statement = connection.createStatement()
        try {
            statement.execute("SELECT 1")
        } finally {
            statement.close()
            connection.close()
        }
        def metrics = reader.collectAllMetrics()

        then:
        metricValue(metrics, CONNECTION_MAX_METRICS, poolName, null) == 7
        metricValue(metrics, CONNECTION_COUNT_METRICS, poolName, "used") != null

        cleanup:
        ctx.close()
    }

    void "test datasource and UCP bean paths share metric registration"() {
        given:
        String poolName = "shared-ucp-pool"
        ApplicationContext ctx = ApplicationContext.run(ucpDataSourceConfiguration(poolName, "ucpShared") + [
                "test.ucp.shared-pool.enabled": "true",
        ])
        def reader = ctx.getBean(InMemoryMetricReader)
        PoolDataSource poolDataSource = ctx.getBean(DataSource).unwrap(PoolDataSource)
        UniversalConnectionPool connectionPool = ctx.getBean(UniversalConnectionPool, Qualifiers.byName("sharedConnectionPool"))

        expect:
        poolDataSource.getConnectionPoolName() == poolName

        when:
        def metrics = reader.collectAllMetrics()

        then:
        metricValue(metrics, CONNECTION_MAX_METRICS, poolName, null) == 7

        when:
        ctx.destroyBean(connectionPool)

        then:
        metricValue(reader.collectAllMetrics(), CONNECTION_MAX_METRICS, poolName, null) == 7

        when:
        ctx.close()

        then:
        !hasMetricForPool(reader.collectAllMetrics(), poolName)

        cleanup:
        ctx.close()
    }

    void "test distinct UCP pool objects with same name have independent registrations"() {
        given:
        def reader = InMemoryMetricReader.create()
        def openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(reader)
                        .build())
                .build()
        def metricsRegistry = new UniversalConnectionPoolMetricsRegistry(new OracleUcpTelemetryConfiguration(openTelemetry))
        def firstConnectionPool = TestUniversalConnectionPoolFactory.connectionPool("same-pool-name", 1, 2, 3, 4)
        def secondConnectionPool = TestUniversalConnectionPoolFactory.connectionPool("same-pool-name", 5, 6, 7, 8)

        when:
        metricsRegistry.register(firstConnectionPool)
        metricsRegistry.register(secondConnectionPool)
        metricsRegistry.unregister(firstConnectionPool)
        def metrics = reader.collectAllMetrics()

        then:
        metricValue(metrics, CONNECTION_COUNT_METRICS, "same-pool-name", "used") == 5
        metricValue(metrics, CONNECTION_MAX_METRICS, "same-pool-name", null) == 7

        cleanup:
        metricsRegistry.unregister(secondConnectionPool)
    }

    void "test unregister ignores unknown and already released UCP pool"() {
        given:
        def reader = InMemoryMetricReader.create()
        def openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(reader)
                        .build())
                .build()
        def metricsRegistry = new UniversalConnectionPoolMetricsRegistry(new OracleUcpTelemetryConfiguration(openTelemetry))
        def connectionPool = TestUniversalConnectionPoolFactory.connectionPool("removed-pool", 1, 2, 3, 4)

        when:
        metricsRegistry.unregister(connectionPool)

        then:
        noExceptionThrown()
        !hasUcpMetrics(reader.collectAllMetrics())

        when:
        metricsRegistry.register(connectionPool)
        metricsRegistry.unregister(connectionPool)
        metricsRegistry.unregister(connectionPool)

        then:
        noExceptionThrown()
        !hasMetricForPool(reader.collectAllMetrics(), "removed-pool")
    }

    void "test managed datasource registrations are cleaned up when a later registration fails"() {
        given:
        def reader = InMemoryMetricReader.create()
        def openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(reader)
                        .build())
                .build()
        def metricsRegistry = new UniversalConnectionPoolMetricsRegistry(new OracleUcpTelemetryConfiguration(openTelemetry))
        def registeredConnectionPool = TestUniversalConnectionPoolFactory.connectionPool("registered-pool", 1, 2, 3, 4)
        UniversalConnectionPoolManager connectionPoolManager = [
                getConnectionPool: { String poolName ->
                    if (poolName == "registered-pool") {
                        return registeredConnectionPool
                    }
                    throw new UniversalConnectionPoolException("missing pool")
                }
        ] as UniversalConnectionPoolManager

        when:
        new ManagedUniversalConnectionPoolMetricsBinder(
                metricsRegistry,
                connectionPoolManager,
                null,
                [poolDataSource("registered-pool"), poolDataSource("missing-pool")])

        then:
        thrown(ConfigurationException)
        !hasMetricForPool(reader.collectAllMetrics(), "registered-pool")
    }

    void "test managed datasource unwraps wrapped PoolDataSource"() {
        given:
        def reader = InMemoryMetricReader.create()
        def openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(reader)
                        .build())
                .build()
        def metricsRegistry = new UniversalConnectionPoolMetricsRegistry(new OracleUcpTelemetryConfiguration(openTelemetry))
        def registeredConnectionPool = TestUniversalConnectionPoolFactory.connectionPool("wrapped-pool", 1, 2, 3, 4)
        UniversalConnectionPoolManager connectionPoolManager = [
                getConnectionPool: { String poolName ->
                    if (poolName == "wrapped-pool") {
                        return registeredConnectionPool
                    }
                    throw new UniversalConnectionPoolException("missing pool")
                }
        ] as UniversalConnectionPoolManager
        def binder = new ManagedUniversalConnectionPoolMetricsBinder(
                metricsRegistry,
                connectionPoolManager,
                null,
                [wrappedDataSource(poolDataSource("wrapped-pool"))])

        expect:
        metricValue(reader.collectAllMetrics(), CONNECTION_MAX_METRICS, "wrapped-pool", null) == 3

        cleanup:
        binder?.close()
    }

    void "test managed datasource skips non UCP datasources"() {
        given:
        def reader = InMemoryMetricReader.create()
        def openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(reader)
                        .build())
                .build()
        def metricsRegistry = new UniversalConnectionPoolMetricsRegistry(new OracleUcpTelemetryConfiguration(openTelemetry))
        def registeredConnectionPool = TestUniversalConnectionPoolFactory.connectionPool("mixed-ucp-pool", 1, 2, 3, 4)
        UniversalConnectionPoolManager connectionPoolManager = [
                getConnectionPool: { String poolName ->
                    if (poolName == "mixed-ucp-pool") {
                        return registeredConnectionPool
                    }
                    throw new UniversalConnectionPoolException("missing pool")
                }
        ] as UniversalConnectionPoolManager
        def binder = new ManagedUniversalConnectionPoolMetricsBinder(
                metricsRegistry,
                connectionPoolManager,
                null,
                [nonUcpDataSource(), poolDataSource("mixed-ucp-pool")])

        expect:
        metricValue(reader.collectAllMetrics(), CONNECTION_MAX_METRICS, "mixed-ucp-pool", null) == 3

        cleanup:
        binder?.close()
    }

    void "test managed datasource does not create missing UCP manager pool from adapter"() {
        given:
        def reader = InMemoryMetricReader.create()
        def openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(reader)
                        .build())
                .build()
        def metricsRegistry = new UniversalConnectionPoolMetricsRegistry(new OracleUcpTelemetryConfiguration(openTelemetry))
        PoolDataSource poolDataSource = PoolDataSourceFactory.getPoolDataSource()
        poolDataSource.setConnectionPoolName("missing-adapter-pool")
        int createdPools = 0
        int startedPools = 0
        UniversalConnectionPoolManager connectionPoolManager = [
                getConnectionPool: { String poolName -> throw new UniversalConnectionPoolException("missing pool") },
                createConnectionPool: { UniversalConnectionPoolAdapter connectionPoolAdapter -> createdPools++ },
                startConnectionPool: { String poolName -> startedPools++ }
        ] as UniversalConnectionPoolManager

        expect:
        poolDataSource instanceof UniversalConnectionPoolAdapter

        when:
        new ManagedUniversalConnectionPoolMetricsBinder(
                metricsRegistry,
                connectionPoolManager,
                null,
                [poolDataSource])

        then:
        thrown(ConfigurationException)
        createdPools == 0
        startedPools == 0
    }

    void "test managed datasource same pool names register distinct pool objects"() {
        given:
        def reader = InMemoryMetricReader.create()
        def openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(reader)
                        .build())
                .build()
        def metricsRegistry = new UniversalConnectionPoolMetricsRegistry(new OracleUcpTelemetryConfiguration(openTelemetry))
        def firstConnectionPool = TestUniversalConnectionPoolFactory.connectionPool("same-managed-pool", 1, 2, 3, 4)
        def secondConnectionPool = TestUniversalConnectionPoolFactory.connectionPool("same-managed-pool", 5, 6, 7, 8)
        def connectionPools = [firstConnectionPool, secondConnectionPool].iterator()
        UniversalConnectionPoolManager connectionPoolManager = [
                getConnectionPool: { String poolName ->
                    if (poolName == "same-managed-pool" && connectionPools.hasNext()) {
                        return connectionPools.next()
                    }
                    throw new UniversalConnectionPoolException("missing pool")
                }
        ] as UniversalConnectionPoolManager
        def binder = new ManagedUniversalConnectionPoolMetricsBinder(
                metricsRegistry,
                connectionPoolManager,
                null,
                [poolDataSource("same-managed-pool"), poolDataSource("same-managed-pool")])

        when:
        metricsRegistry.unregister(firstConnectionPool)
        def metrics = reader.collectAllMetrics()

        then:
        metricValue(metrics, CONNECTION_COUNT_METRICS, "same-managed-pool", "used") == 5
        metricValue(metrics, CONNECTION_MAX_METRICS, "same-managed-pool", null) == 7

        cleanup:
        binder?.close()
    }

    void "test managed datasource registers naturally-created UCP manager pool from unwrapped adapter"() {
        given:
        String poolName = "wrapped-real-ucp-pool"
        CountingConnectionFactory.reset()
        ApplicationContext ctx = ApplicationContext.run(ucpDataSourceConfiguration(
                poolName,
                "ucpWrappedWithJdbc",
                CountingConnectionFactory.name))
        def reader = ctx.getBean(InMemoryMetricReader)
        def dataSource = ctx.getBean(DataSource)
        def poolDataSource = dataSource.unwrap(PoolDataSource)
        def connectionPoolManager = ctx.getBean(UniversalConnectionPoolManager)
        boolean poolRegisteredAtStartup = SharedUniversalConnectionPoolFactory.hasConnectionPool(connectionPoolManager, poolName)

        expect:
        !(dataSource instanceof PoolDataSource)
        dataSource.isWrapperFor(PoolDataSource)
        poolDataSource.getConnectionPoolName() == poolName
        poolRegisteredAtStartup
        CountingConnectionFactory.connectionsCreated == 0

        when:
        def connection = poolDataSource.connection
        def statement = connection.createStatement()
        try {
            statement.execute("SELECT 1")
        } finally {
            statement.close()
            connection.close()
        }
        def metrics = reader.collectAllMetrics()

        then:
        metricValue(metrics, CONNECTION_MAX_METRICS, poolName, null) == 7
        metricValue(metrics, CONNECTION_COUNT_METRICS, poolName, "used") != null
        CountingConnectionFactory.connectionsCreated > 0

        cleanup:
        ctx.close()
        CountingConnectionFactory.reset()
    }

    void "test SQLException from wrapped datasource unwrap fails and cleans up earlier registrations"() {
        given:
        def reader = InMemoryMetricReader.create()
        def openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(reader)
                        .build())
                .build()
        def metricsRegistry = new UniversalConnectionPoolMetricsRegistry(new OracleUcpTelemetryConfiguration(openTelemetry))
        def registeredConnectionPool = TestUniversalConnectionPoolFactory.connectionPool("registered-pool", 1, 2, 3, 4)
        UniversalConnectionPoolManager connectionPoolManager = [
                getConnectionPool: { String poolName ->
                    if (poolName == "registered-pool") {
                        return registeredConnectionPool
                    }
                    throw new UniversalConnectionPoolException("missing pool")
                }
        ] as UniversalConnectionPoolManager

        when:
        new ManagedUniversalConnectionPoolMetricsBinder(
                metricsRegistry,
                connectionPoolManager,
                null,
                [poolDataSource("registered-pool"), throwingWrappedDataSource()])

        then:
        thrown(ConfigurationException)
        !hasMetricForPool(reader.collectAllMetrics(), "registered-pool")
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

    void "test Oracle UCP telemetry disabled for managed datasource"() {
        given:
        String poolName = "disabled-ucp-pool"
        ApplicationContext ctx = ApplicationContext.run(ucpDataSourceConfiguration(poolName, "ucpDisabled") + [
                "otel.instrumentation.ucp.enabled": "false",
                "otel.instrumentation.jdbc.enabled": "false",
        ])
        def reader = ctx.getBean(InMemoryMetricReader)

        expect:
        ctx.getBean(DataSource).unwrap(PoolDataSource).getConnectionPoolName() == poolName
        ctx.findBean(UniversalConnectionPoolBeanEventListener).isEmpty()
        ctx.findBean(OracleUcpTelemetryConfiguration).isEmpty()
        ctx.findBean(ManagedUniversalConnectionPoolMetricsBinder).isEmpty()
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

    private static PoolDataSource poolDataSource(String poolName) {
        [
                getConnectionPoolName: { poolName }
        ] as PoolDataSource
    }

    private static DataSource wrappedDataSource(PoolDataSource poolDataSource) {
        [
                isWrapperFor: { Class<?> type -> type == PoolDataSource },
                unwrap: { Class<?> type ->
                    if (type == PoolDataSource) {
                        return poolDataSource
                    }
                    throw new SQLException("Unsupported unwrap type")
                }
        ] as DataSource
    }

    private static DataSource throwingWrappedDataSource() {
        [
                isWrapperFor: { Class<?> type -> type == PoolDataSource },
                unwrap: { Class<?> type -> throw new SQLException("Cannot unwrap ${type.name}") }
        ] as DataSource
    }

    private static DataSource nonUcpDataSource() {
        [
                isWrapperFor: { Class<?> type -> false },
                unwrap: { Class<?> type -> throw new SQLException("Unsupported unwrap type") }
        ] as DataSource
    }

    private static Map<String, Object> ucpDataSourceConfiguration(
            String poolName,
            String databaseName,
            String connectionFactoryClassName = "org.h2.jdbcx.JdbcDataSource") {
        [
                "datasources.default.connection-pool-name": poolName,
                "datasources.default.url": "jdbc:h2:mem:${databaseName};LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
                "datasources.default.username": "sa",
                "datasources.default.connection-factory-class-name": connectionFactoryClassName,
                "datasources.default.initial-pool-size": 0,
                "datasources.default.min-pool-size": 0,
                "datasources.default.max-pool-size": 7,
        ]
    }
}
