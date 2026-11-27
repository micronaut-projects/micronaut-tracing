package io.micronaut.tracing.opentelemetry.instrument.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.type.Argument
import io.micronaut.tracing.util.LazyDataSourceFactory
import io.micronaut.tracing.util.OpenTelemetryCreations
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import spock.lang.Specification

import javax.sql.DataSource

class JdbcTelemetryBeanCreationSpec extends Specification {

    void "test jdbc telemetry enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        def dataSourceBeanCreatedEventListener= ctx.getBean(DataSourceBeanCreatedEventListener)
        def jdbcTelemetryConfiguration = ctx.getBean(JdbcTelemetryConfiguration)

        then:
        dataSourceBeanCreatedEventListener
        jdbcTelemetryConfiguration

        cleanup:
        ctx.close()
    }

    void "test jdbc telemetry starts in k8s cloud environment"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'datasources.default.dialect': 'H2',
                'micronaut.application.name': 'otel-test',
                'datasources.default.url': 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.default.username': 'sa',
                'datasources.default.driver-class-name': 'org.h2.Driver'
        ], "k8s", "cloud")

        expect:
        ctx.getBean(DataSourceBeanCreatedEventListener)
        ctx.getBean(JdbcTelemetryConfiguration)
        ctx.getBean(Argument.of(ApplicationEventPublisher, StartupEvent))

        cleanup:
        ctx.close()
    }

    void "test listener creation defers JDBC telemetry until a DataSource is wrapped"() {
        given: "a started context where the DataSource is a plain singleton, created on first lookup"
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.application.name': 'otel-test',
                (LazyDataSourceFactory.URL_PROPERTY): 'jdbc:h2:mem:lazyDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
        ], "k8s", "cloud")
        OpenTelemetryCreations creations = ctx.getBean(OpenTelemetryCreations)

        expect: "starting the context did not initialize OpenTelemetry"
        creations.count() == 0

        when: "the listener bean is created, as the context does for the first DataSource creation event"
        ctx.getBean(DataSourceBeanCreatedEventListener)

        then: "creating the listener does not initialize OpenTelemetry or the JDBC telemetry configuration"
        creations.count() == 0

        when: "the DataSource is created"
        DataSource dataSource = ctx.getBean(DataSource)
        executeSql(ctx, "CREATE TABLE foo (id INT PRIMARY KEY, name VARCHAR(255))")

        then: "the listener wrapped it with the JDBC telemetry built from OpenTelemetry, initialized exactly once"
        dataSource.class.simpleName == 'OpenTelemetryDataSource'
        creations.count() == 1
        ctx.getBean(InMemorySpanExporter).finishedSpanItems.size() == 1

        cleanup:
        ctx.close()
    }

    void "test jdbc disabled with property"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                "otel.instrumentation.jdbc.enabled": "false",
        ])

        when:
        def dataSourceBeanCreatedEventListener = ctx.findBean(DataSourceBeanCreatedEventListener)
        def jdbcTelemetryConfiguration = ctx.findBean(JdbcTelemetryConfiguration)

        then:
        dataSourceBeanCreatedEventListener.isEmpty()
        jdbcTelemetryConfiguration.isEmpty()

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
