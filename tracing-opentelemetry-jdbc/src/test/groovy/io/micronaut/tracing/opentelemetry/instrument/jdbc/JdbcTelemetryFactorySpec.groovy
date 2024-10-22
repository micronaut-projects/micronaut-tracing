package io.micronaut.tracing.opentelemetry.instrument.jdbc

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class JdbcTelemetryFactorySpec extends Specification {

    void "test jdbc telemetry enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        def jdbcTelemetryFactory = ctx.getBean(JdbcTelemetryFactory)
        def jdbcTelemetryConfiguration = ctx.getBean(JdbcTelemetryConfiguration)

        then:
        jdbcTelemetryFactory
        jdbcTelemetryConfiguration

        cleanup:
        ctx.close()
    }

    void "test jdbc disabled with property"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                "otel.instrumentation.jdbc.enabled": "false",
        ])

        when:
        def jdbcTelemetryFactory = ctx.findBean(JdbcTelemetryFactory)
        def jdbcTelemetryConfiguration = ctx.findBean(JdbcTelemetryConfiguration)

        then:
        jdbcTelemetryFactory.isEmpty()
        jdbcTelemetryConfiguration.isEmpty()

        cleanup:
        ctx.close()
    }
}

