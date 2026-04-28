package io.micronaut.tracing.opentelemetry.instrument.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.type.Argument
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

    void "test jdbc telemetry listener does not eagerly create open telemetry during startup"() {
        when:
        ApplicationContext ctx = ApplicationContext.run([
                "micronaut.eager-init.singletons": "true",
                "test.open-telemetry.requires-startup-event-publisher": "true",
        ])

        then:
        ctx.containsBean(DataSourceBeanCreatedEventListener)

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
