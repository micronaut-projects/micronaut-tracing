package io.micronaut.tracing.brave

import brave.Tracing
import brave.http.HttpClientHandler
import brave.http.HttpServerHandler
import brave.http.HttpTracing
import io.micronaut.context.ApplicationContext
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.noop.NoopTracer
import spock.lang.Specification
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.Reporter

import static io.micronaut.tracing.brave.sender.HttpClientSender.Builder.DEFAULT_SERVER_URL

/**
 * @author graemerocher
 * @since 1.0
 */
class BraveTracerFactorySpec extends Specification {

    void 'test brave tracer configuration'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'tracing.zipkin.enabled': true,
                'tracing.zipkin.http.endpoint': DEFAULT_SERVER_URL
        )

        expect: 'The tracer is obtained'
        context.getBean AsyncReporter
        context.getBean Tracer
        context.getBean Tracing
        context.getBean HttpTracing
        context.getBean HttpClientHandler
        context.getBean HttpServerHandler

        cleanup:
        context.close()
    }

    void 'test brave tracer configuration no endpoint'() {
        given:
        ApplicationContext context = ApplicationContext.run('tracing.zipkin.enabled': true)

        expect: 'The tracer is obtained'
        !context.containsBean(Reporter)
        context.getBean Tracer
        context.getBean Tracing
        context.getBean HttpTracing
        context.getBean HttpClientHandler
        context.getBean HttpServerHandler

        cleanup:
        context.close()
    }

}
