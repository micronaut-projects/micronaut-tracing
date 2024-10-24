package io.micronaut.tracing.opentelemetry.instrument.jdbc

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

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
}

