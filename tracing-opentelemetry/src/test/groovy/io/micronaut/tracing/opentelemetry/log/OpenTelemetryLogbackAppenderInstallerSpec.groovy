/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.opentelemetry.log

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import io.micronaut.context.ApplicationContext
import io.opentelemetry.api.OpenTelemetry
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class OpenTelemetryLogbackAppenderInstallerSpec extends Specification {

    private static final String APPENDER_NAME = 'OTEL'

    @AutoCleanup
    ApplicationContext context

    void 'installs OpenTelemetry on the logback appender automatically'() {
        given:
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
        def appender = new io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender()
        appender.name = APPENDER_NAME
        appender.context = loggerContext
        appender.start()
        rootLogger.addAppender(appender)
        assert readOpenTelemetry(appender) == null

        when:
        context = ApplicationContext.run()

        then:
        readOpenTelemetry(appender) != null
        readOpenTelemetry(appender) == context.getBean(OpenTelemetry)

        cleanup:
        uninstallOpenTelemetry(appender)
        rootLogger.detachAppender(APPENDER_NAME)
        appender.stop()
    }

    void 'does not install OpenTelemetry on the logback appender when micronaut otel is disabled'() {
        given:
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
        def appender = new io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender()
        appender.name = APPENDER_NAME
        appender.context = loggerContext
        appender.start()
        rootLogger.addAppender(appender)
        assert readOpenTelemetry(appender) == null

        when:
        context = ApplicationContext.run(
            'micronaut.otel.enabled': false
        )

        then:
        readOpenTelemetry(appender) == null

        cleanup:
        uninstallOpenTelemetry(appender)
        rootLogger.detachAppender(APPENDER_NAME)
        appender.stop()
    }

    private static OpenTelemetry readOpenTelemetry(io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender appender) {
        Field field = io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.class.getDeclaredField('openTelemetry')
        field.accessible = true
        return (OpenTelemetry) field.get(appender)
    }

    private static void uninstallOpenTelemetry(io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender appender) {
        Field field = io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.class.getDeclaredField('openTelemetry')
        field.accessible = true
        field.set(Modifier.isStatic(field.modifiers) ? null : appender, null)
    }
}
