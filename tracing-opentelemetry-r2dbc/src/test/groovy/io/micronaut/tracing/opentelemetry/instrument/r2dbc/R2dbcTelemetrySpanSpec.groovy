package io.micronaut.tracing.opentelemetry.instrument.r2dbc

import io.micronaut.context.ApplicationContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration

class R2dbcTelemetrySpanSpec extends Specification {

    void "test r2dbc telemetry enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.application.name': 'otel-test',
                'r2dbc.datasources.default.url': 'r2dbc:h2:mem:///devDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
        ])

        when:
        ConnectionFactory connectionFactory = ctx.getBean(ConnectionFactory)
        InMemorySpanExporter inMemorySpanExporter = ctx.getBean(InMemorySpanExporter)
        executeStatements(connectionFactory,
                'CREATE TABLE foo (id INT PRIMARY KEY, name VARCHAR(255))',
                "INSERT INTO foo (id, name) VALUES (1, 'Micronaut')")
        def dbStatements = inMemorySpanExporter.getFinishedSpanItems()
                .collect { it.attributes.get(AttributeKey.stringKey('db.statement')) }
                .findAll { it != null }

        then:
        dbStatements.any { it.contains('CREATE TABLE foo') }
        dbStatements.any { it.contains('INSERT INTO foo') }

        cleanup:
        ctx.close()
    }

    private static void executeStatements(ConnectionFactory connectionFactory, String... statements) {
        Mono.usingWhen(
                Mono.from(connectionFactory.create()),
                connection -> Flux.fromIterable(statements.toList())
                        .concatMap(statement -> Flux.from(connection.createStatement(statement).execute())
                                .concatMap(result -> result.rowsUpdated))
                        .then(),
                connection -> Mono.from(connection.close())
        ).block(Duration.ofSeconds(10))
    }
}
