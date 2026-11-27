package io.micronaut.tracing.opentelemetry.instrument.jdbc

import io.micronaut.context.ApplicationContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import spock.lang.Specification

import javax.sql.DataSource

class JdbcTelemetrySpanSpec  extends Specification {

    void "test jdbc telemetry enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'datasources.default.dialect': 'H2',
                'micronaut.application.name': 'otel-test',
                'datasources.default.url': 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.default.username': 'sa',
                'datasources.default.driver-class-name': 'org.h2.Driver'
        ])

        when:
        def dataSourceBeanCreatedEventListener= ctx.getBean(DataSourceBeanCreatedEventListener)
        def jdbcTelemetryConfiguration = ctx.getBean(JdbcTelemetryConfiguration)
        def inMemorySpanExporter = ctx.getBean(InMemorySpanExporter)
        executeSql(ctx, "CREATE TABLE foo (id INT PRIMARY KEY, name VARCHAR(255))")
        executeSql(ctx, "DROP TABLE foo")

        then:
        dataSourceBeanCreatedEventListener
        jdbcTelemetryConfiguration
        inMemorySpanExporter
        def finishedSpanItems = inMemorySpanExporter.getFinishedSpanItems()

        finishedSpanItems.size() == 2

        finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("db.statement")).contains("DROP TABLE foo"))
        finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("db.statement")).contains("CREATE TABLE foo"))

        cleanup:
        ctx.close()
    }

    void "test jdbc telemetry statement-instrumenter-enabled false"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
            'datasources.default.dialect': 'H2',
            'micronaut.application.name': 'otel-test',
            'datasources.default.url': 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
            'datasources.default.username': 'sa',
            'datasources.default.driver-class-name': 'org.h2.Driver',
            'otel.instrumentation.jdbc.data-source-instrumenter-enabled': 'false',
            'otel.instrumentation.jdbc.statement-instrumenter-enabled': 'false',
            'otel.instrumentation.jdbc.statement-sanitization-enabled': 'false'
        ])

        when:
        def dataSourceBeanCreatedEventListener= ctx.getBean(DataSourceBeanCreatedEventListener)
        def jdbcTelemetryConfiguration = ctx.getBean(JdbcTelemetryConfiguration)
        def inMemorySpanExporter = ctx.getBean(InMemorySpanExporter)
        executeSql(ctx, "CREATE TABLE foo (id INT PRIMARY KEY, name VARCHAR(255))")
        executeSql(ctx, "DROP TABLE foo")

        then:
        dataSourceBeanCreatedEventListener
        jdbcTelemetryConfiguration
        inMemorySpanExporter
        def finishedSpanItems = inMemorySpanExporter.getFinishedSpanItems()

        finishedSpanItems.size() == 0
        !jdbcTelemetryConfiguration.builder.querySanitizationEnabled
        !jdbcTelemetryConfiguration.builder.statementInstrumenterEnabled
        !jdbcTelemetryConfiguration.builder.dataSourceInstrumenterEnabled

        cleanup:
        ctx.close()
    }

    private static void executeSql(ApplicationContext ctx, String sql) {
        ctx.getBean(DataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute(sql)
            }
        }
    }

}
