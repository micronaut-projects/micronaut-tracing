package io.micronaut.tracing.opentracing

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.opentracing.Tracer
import org.slf4j.LoggerFactory
import spock.lang.Specification

class OpenTracingDeprecationWarningSpec extends Specification {

    ListAppender<ILoggingEvent> appender = new ListAppender<>()
    Logger logger = (Logger) LoggerFactory.getLogger(OpenTracingDeprecationWarning)

    void setup() {
        OpenTracingDeprecationWarning.reset()
        appender.start()
        logger.addAppender(appender)
    }

    void cleanup() {
        logger.detachAppender(appender)
        OpenTracingDeprecationWarning.reset()
    }

    void 'warns once when the Jaeger tracer is created'() {
        when:
        ApplicationContext ctx = ApplicationContext.run('tracing.jaeger.enabled': 'true')
        ctx.getBean(Tracer)
        ctx.close()
        ctx = ApplicationContext.run('tracing.jaeger.enabled': 'true')
        ctx.getBean(Tracer)
        ctx.close()

        then:
        appender.list.size() == 1
        appender.list[0].level == Level.WARN
        appender.list[0].formattedMessage.contains('JaegerTracer')
        appender.list[0].formattedMessage.contains('#migrationFromOpenTracing')
    }

    void 'does not warn for the default no-op tracer'() {
        when:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(Tracer)
        ctx.close()

        then:
        appender.list.empty
    }
}
