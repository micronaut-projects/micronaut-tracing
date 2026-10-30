package io.micronaut.tracing.opentracing.instrument.http

import io.jaegertracing.internal.JaegerSpan
import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.ServerFilterChain
import io.opentracing.Tracer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class OpenTracingFilterCancellationSpec extends Specification {

    @AutoCleanup
    ApplicationContext context

    InMemoryReporter reporter

    Tracer tracer

    void setup() {
        reporter = new InMemoryReporter()
        context = ApplicationContext.builder([
                'tracing.jaeger.enabled'            : true,
                'tracing.jaeger.sampler.probability': 1
        ]).singletons(reporter).start()
        tracer = context.getBean(Tracer)
    }

    void 'client span is finished when subscription is cancelled'() {
        given:
        def filter = new OpenTracingClientFilter(tracer, ConversionService.SHARED, null)
        def publisher = filter.doFilter(HttpRequest.GET('/cancel-client'), { request ->
            Mono.never()
        } as ClientFilterChain)

        when:
        def subscription = Mono.from(publisher).subscribe()
        subscription.dispose()

        then:
        new PollingConditions().eventually {
            reporter.spans.size() == 1

            JaegerSpan span = reporter.spans[0]
            span.operationName == 'GET /cancel-client'
            span.tags['http.client'] == true
            span.tags['span.kind'] == 'client'
            span.tags['http.path'] == '/cancel-client'
            tracer.activeSpan() == null
        }
    }

    void 'server span is finished when subscription is cancelled'() {
        given:
        def filter = new OpenTracingServerFilter(tracer, ConversionService.SHARED, null)
        def publisher = filter.doFilter(HttpRequest.GET('/cancel-server'), { request ->
            Mono.never()
        } as ServerFilterChain)

        when:
        def subscription = Mono.from(publisher).subscribe()
        subscription.dispose()

        then:
        new PollingConditions().eventually {
            reporter.spans.size() == 1

            JaegerSpan span = reporter.spans[0]
            span.operationName == 'GET /cancel-server'
            span.tags['http.server'] == true
            span.tags['span.kind'] == 'server'
            span.tags['http.path'] == '/cancel-server'
            tracer.activeSpan() == null
        }
    }
}
