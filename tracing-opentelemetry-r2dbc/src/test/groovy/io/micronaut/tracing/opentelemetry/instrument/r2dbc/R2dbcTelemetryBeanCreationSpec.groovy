package io.micronaut.tracing.opentelemetry.instrument.r2dbc

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class R2dbcTelemetryBeanCreationSpec extends Specification {

    void "test r2dbc telemetry enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        def r2dbcConnectionFactoryFactory = ctx.getBean(R2dbcConnectionFactoryFactory)
        def r2dbcTelemetryConfiguration = ctx.getBean(R2dbcTelemetryConfiguration)

        then:
        r2dbcConnectionFactoryFactory
        r2dbcTelemetryConfiguration

        cleanup:
        ctx.close()
    }

    void "test r2dbc telemetry disabled with property"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                "otel.instrumentation.r2dbc.enabled": "false",
        ])

        when:
        def r2dbcConnectionFactoryFactory = ctx.findBean(R2dbcConnectionFactoryFactory)
        def r2dbcTelemetryConfiguration = ctx.findBean(R2dbcTelemetryConfiguration)

        then:
        r2dbcConnectionFactoryFactory.isEmpty()
        r2dbcTelemetryConfiguration.isEmpty()

        cleanup:
        ctx.close()
    }
}
