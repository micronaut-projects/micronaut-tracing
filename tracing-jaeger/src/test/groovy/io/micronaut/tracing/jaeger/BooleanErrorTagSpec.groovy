package io.micronaut.tracing.jaeger

import io.jaegertracing.internal.JaegerSpan
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory
import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/941')
class BooleanErrorTagSpec extends Specification {

    @AutoCleanup
    ApplicationContext context

    PollingConditions conditions = new PollingConditions()

    void 'the error tag is a boolean and the message a log event when tracing.opentracing.boolean-error-tag is enabled'() {
        given:
        InMemoryReporter reporter = start(true)

        when:
        client().toBlocking().exchange('/boolean-error-tag/fail', String)

        then:
        thrown(HttpClientResponseException)
        conditions.eventually {
            reporter.spans.size() == 2

            JaegerSpan clientSpan = reporter.spans.find { it.tags.containsKey('http.client') }
            clientSpan.tags['error'] == true
            clientSpan.tags['http.status_code'] == 500
            clientSpan.logs.any { it.fields['event'] == 'error' && it.fields['message'] == 'Internal Server Error' }

            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags['error'] == true
            serverSpan.tags['http.status_code'] == 500
            serverSpan.logs.any { it.fields['event'] == 'error' && it.fields['message'] == 'Internal Server Error' }
        }
    }

    void 'the error tag holds the message by default'() {
        given:
        InMemoryReporter reporter = start(false)

        when:
        client().toBlocking().exchange('/boolean-error-tag/fail', String)

        then:
        thrown(HttpClientResponseException)
        conditions.eventually {
            reporter.spans.size() == 2
            reporter.spans.find { it.tags.containsKey('http.client') }.tags['error'] == 'Internal Server Error'
            reporter.spans.find { it.tags.containsKey('http.server') }.tags['error'] == 'Internal Server Error'
        }
    }

    private InMemoryReporter start(boolean booleanErrorTag) {
        Map<String, Object> properties = [
            'spec.name'                   : 'BooleanErrorTagSpec',
            'tracing.jaeger.enabled'      : true,
            'tracing.jaeger.sampler.probability': 1
        ]
        if (booleanErrorTag) {
            properties['tracing.opentracing.boolean-error-tag'] = true
        }
        context = ApplicationContext.builder(properties)
            .singletons(new InMemoryReporter(), new InMemoryMetricsFactory())
            .start()
        return context.getBean(InMemoryReporter)
    }

    private HttpClient client() {
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        return context.createBean(HttpClient, server.URL)
    }

    @Requires(property = 'spec.name', value = 'BooleanErrorTagSpec')
    @Controller('/boolean-error-tag')
    static class FailingController {

        @Get('/fail')
        String fail() {
            throw new IllegalStateException('Bad things happened')
        }
    }
}
