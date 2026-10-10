package io.micronaut.tracing.opentelemetry.log

import io.micronaut.context.ApplicationContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.lang.reflect.Field

class OpenTelemetryLog4jAppenderInstallerSpec extends Specification {

    private static final String APPENDER_NAME = 'OTEL_LOG4J'

    @AutoCleanup
    ApplicationContext context

    OpenTelemetryAppender appender

    void setup() {
        appender = OpenTelemetryAppender.builder().setName(APPENDER_NAME).build()
        appender.start()
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false)
        loggerContext.configuration.addAppender(appender)
        loggerContext.updateLoggers()
    }

    void cleanup() {
        appender.openTelemetry = null
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false)
        loggerContext.configuration.appenders.remove(APPENDER_NAME)
        appender.stop()
    }

    void 'installs OpenTelemetry on the log4j appender automatically'() {
        given:
        assert readOpenTelemetry(appender) == null

        when:
        context = ApplicationContext.run('otel.register.global': false)

        then:
        context.containsBean(OpenTelemetryLog4jAppenderInstaller)
        readOpenTelemetry(appender) == context.getBean(OpenTelemetry)
    }

    void 'does not install OpenTelemetry on the log4j appender when micronaut otel is disabled'() {
        when:
        context = ApplicationContext.run('micronaut.otel.enabled': false)

        then:
        !context.containsBean(OpenTelemetryLog4jAppenderInstaller)
        readOpenTelemetry(appender) == null
    }

    private static OpenTelemetry readOpenTelemetry(OpenTelemetryAppender appender) {
        Field field = OpenTelemetryAppender.getDeclaredField('openTelemetry')
        field.accessible = true
        return (OpenTelemetry) field.get(appender)
    }
}
