package io.micronaut.tracing.opentelemetry.instrument.ucp

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class OracleUcpTelemetryBeanCreationSpec extends Specification {

    void "test Oracle UCP telemetry enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        def universalConnectionPoolBeanEventListener= ctx.getBean(UniversalConnectionPoolBeanEventListener)
        def oracleUcpTelemetryConfiguration = ctx.getBean(OracleUcpTelemetryConfiguration)

        then:
        universalConnectionPoolBeanEventListener
        oracleUcpTelemetryConfiguration

        cleanup:
        ctx.close()
    }

    void "test Oracle UCP telemetry disabled with property"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                "otel.instrumentation.ucp.enabled": "false",
        ])

        when:
        def universalConnectionPoolBeanEventListener = ctx.findBean(UniversalConnectionPoolBeanEventListener)
        def oracleUcpTelemetryConfiguration = ctx.findBean(OracleUcpTelemetryConfiguration)

        then:
        universalConnectionPoolBeanEventListener.isEmpty()
        oracleUcpTelemetryConfiguration.isEmpty()

        cleanup:
        ctx.close()
    }
}

