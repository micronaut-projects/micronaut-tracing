package io.micronaut.tracing.opentelemetry.instrument.jdbc

import io.micronaut.context.ApplicationContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import spock.lang.Specification

class JdbcTelemetrySpanSpec  extends Specification {

    void "test jdbc telemetry enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'datasources.default.dialect': 'H2',
                'micronaut.application.name': 'otel-test',
                'datasources.default.schema-generate': 'CREATE_DROP',
                'datasources.default.url': 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.default.username': 'sa',
                'datasources.default.driver-class-name': 'org.h2.Driver'
        ])

        when:
        def jdbcTelemetryFactory = ctx.getBean(JdbcTelemetryFactory)
        def jdbcTelemetryConfiguration = ctx.getBean(JdbcTelemetryConfiguration)
        def InMemorySpanExporter = ctx.getBean(InMemorySpanExporter)

        then:
        jdbcTelemetryFactory
        jdbcTelemetryConfiguration
        InMemorySpanExporter
        def finishedSpanItems = InMemorySpanExporter.getFinishedSpanItems()

        finishedSpanItems.size() == 2

        finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("db.statement")).contains("DROP TABLE `foo`"))
        finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("db.statement")).contains("CREATE TABLE `foo`"))

        cleanup:
        ctx.close()
    }






}
