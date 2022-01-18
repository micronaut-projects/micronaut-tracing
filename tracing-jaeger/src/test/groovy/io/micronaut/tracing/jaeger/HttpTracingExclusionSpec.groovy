package io.micronaut.tracing.jaeger

import io.jaegertracing.internal.JaegerSpan
import io.jaegertracing.internal.JaegerTracer
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory
import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.TimeUnit

/**
 * @author graemerocher
 * @since 1.0
 */
class HttpTracingExclusionSpec extends Specification {

    @AutoCleanup
    private ApplicationContext context

    private PollingConditions conditions = new PollingConditions()
    private EmbeddedServer embeddedServer
    private HttpClient client
    private InMemoryReporter reporter

    void setup() {
        context = ApplicationContext
                .builder('tracing.jaeger.enabled': true,
                         'tracing.jaeger.sampler.probability': 1,
                         'tracing.exclusions[0]': '.*hello.*')
                .singletons(
                        new InMemoryReporter(),
                        new InMemoryMetricsFactory())
                .start()

        embeddedServer = context.getBean(EmbeddedServer).start()
        client = context.createBean(HttpClient, embeddedServer.URL)
        reporter = context.getBean(InMemoryReporter)
    }

    void 'test basic HTTP tracing'() {
        expect:
        context.containsBean(JaegerTracer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/hello/John', String)

        then:
        response
        reporter.spans.empty
    }

    void 'test basic HTTP tracing - blocking controller method'() {
        expect:
        context.containsBean(JaegerTracer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/blocking/hello/John', String)

        then:
        response
        reporter.spans.empty
    }

    void 'test basic response reactive HTTP tracing'() {
        expect:
        context.containsBean(JaegerTracer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/response-reactive/John', String)

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 2

            JaegerSpan span = reporter.spans.find { it.operationName == 'GET /traced/response-reactive/{name}' }
            span != null
            span.tags['foo'] == 'bar'
            span.tags['http.path'] == '/traced/response-reactive/John'

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test reactive HTTP tracing'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/reactive/John', String)

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 2

            JaegerSpan span = reporter.spans.find { it.operationName == 'GET /traced/reactive/{name}' }
            span != null
            span.tags['foo'] == 'bar'
            span.tags['http.path'] == '/traced/reactive/John'

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test basic HTTP trace error'() {
        when:
        client.toBlocking().exchange('/traced/error/John', String)

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<?> response = e.response
        response

        conditions.eventually {
            reporter.spans.size() == 2

            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags['http.path'] == '/traced/error/John'
            span.tags['http.status_code'] == 500
            span.tags['http.method'] == 'GET'
            span.tags['error'] == 'Internal Server Error'
            span.operationName == 'GET /traced/error/John'

            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags['http.path'] == '/traced/error/John'
            serverSpan.tags['http.status_code'] == 500
            serverSpan.tags['http.method'] == 'GET'
            serverSpan.tags['error'] == 'Internal Server Error'
            serverSpan.operationName == 'GET /traced/error/{name}'

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test basic HTTP trace error - blocking controller method'() {
        when:
        client.toBlocking().exchange('/traced/blocking/error/John', String)

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<?> response = e.response
        response

        conditions.eventually {
            reporter.spans.size() == 2

            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags['http.path'] == '/traced/blocking/error/John'
            span.tags['http.status_code'] == 500
            span.tags['http.method'] == 'GET'
            span.tags['error'] == 'Internal Server Error'
            span.operationName == 'GET /traced/blocking/error/John'

            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags['http.path'] == '/traced/blocking/error/John'
            serverSpan.tags['http.status_code'] == 500
            serverSpan.tags['http.method'] == 'GET'
            serverSpan.tags['error'] == 'Internal Server Error'
            serverSpan.operationName == 'GET /traced/blocking/error/{name}'

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test basic HTTP trace error - reactive'() {
        when:
        client.toBlocking().exchange('/traced/reactiveError/John', String)

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<?> response = e.response
        response

        conditions.eventually {
            reporter.spans.size() == 2

            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags['http.path'] == '/traced/reactiveError/John'
            span.tags['http.status_code'] == 500
            span.tags['http.method'] == 'GET'
            span.tags['error'] == 'Internal Server Error'
            span.operationName == 'GET /traced/reactiveError/John'

            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags['http.path'] == '/traced/reactiveError/John'
            serverSpan.tags['http.status_code'] == 500
            serverSpan.tags['http.method'] == 'GET'
            serverSpan.tags['error'] == 'Internal Server Error'
            serverSpan.operationName == 'GET /traced/reactiveError/{name}'

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test basic HTTP trace error - reactive with error handler'() {
        when:
        client.toBlocking().exchange('/traced/quota-error', String)

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<?> response = e.response
        response

        conditions.eventually {
            reporter.spans.size() == 2

            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags['http.path'] == '/traced/quota-error'
            span.tags['http.status_code'] == 429
            span.tags['http.method'] == 'GET'
            span.tags['error'] == 'retry later'
            span.operationName == 'GET /traced/quota-error'

            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags['http.path'] == '/traced/quota-error'
            serverSpan.tags['http.status_code'] == 429
            serverSpan.tags['http.method'] == 'GET'
            serverSpan.tags['error'] == 'Too Many Requests'
            serverSpan.operationName == 'GET /traced/quota-error'

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test delayed HTTP trace error'() {
        when:
        client.toBlocking().exchange('/traced/delayed-error/2s', String)

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<?> response = e.response
        response

        conditions.eventually {
            reporter.spans.size() == 2

            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags['http.path'] == '/traced/delayed-error/2s'
            span.tags['http.status_code'] == 500
            span.tags['http.method'] == 'GET'
            span.tags['error'] == 'Internal Server Error'
            span.operationName == 'GET /traced/delayed-error/2s'

            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags['http.path'] == '/traced/delayed-error/2s'
            serverSpan.tags['http.status_code'] == 500
            serverSpan.tags['http.method'] == 'GET'
            serverSpan.tags['error'] == 'Internal Server Error'
            serverSpan.duration > TimeUnit.SECONDS.toMicros(2L)
            serverSpan.operationName == 'GET /traced/delayed-error/{duration}'

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test continue HTTP tracing'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/continued/John', String)

        then:
        response

        conditions.eventually {
            reporter.spans.size() == 2

            reporter.spans.find {
                it.operationName == 'GET /traced/continued/{name}' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/continued/John' &&
                        it.tags['http.server']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/continued/John' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/continued/John' &&
                        it.tags['http.client']
            } != null

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test continue HTTP tracing - blocking controller method'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/blocking/continued/John', String)

        then:
        response

        conditions.eventually {
            reporter.spans.size() == 2

            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/continued/{name}' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/blocking/continued/John' &&
                        it.tags['http.server']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/continued/John' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/blocking/continued/John' &&
                        it.tags['http.client']
            } != null

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test continue HTTP tracing - reactive'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/continueRx/John', String)

        then:
        response

        conditions.eventually {
            reporter.spans.size() == 2

            reporter.spans.find {
                it.operationName == 'GET /traced/continueRx/{name}' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/continueRx/John' &&
                        it.tags['http.server']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/continueRx/John' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/continueRx/John' &&
                        it.tags['http.client']
            } != null

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test nested HTTP tracing'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)

        then:
        response

        conditions.eventually {
            reporter.spans.size() == 2

            reporter.spans.find {
                it.operationName == 'GET /traced/nested/{name}' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/nested/John' &&
                        it.tags['http.server']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/nested/John' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/nested/John' &&
                        it.tags['http.client']
            } != null

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test nested HTTP tracing - blocking controller method'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/blocking/nested/John', String)

        then:
        response

        conditions.eventually {
            reporter.spans.size() == 2

            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/nested/{name}' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/blocking/nested/John' &&
                        it.tags['http.server']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/nested/John' &&
                        !it.tags['foo'] &&
                        it.tags['http.path'] == '/traced/blocking/nested/John' &&
                        it.tags['http.client']
            } != null

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test nested HTTP error tracing'() {
        when:
        client.toBlocking().exchange('/traced/nestedError/John', String)

        then:
        HttpClientResponseException e = thrown()
        e != null

        conditions.eventually {
            reporter.spans.size() == 4

            reporter.spans.find {
                it.operationName == 'GET /traced/error/{name}' &&
                        it.tags.containsKey('error') &&
                        it.tags['http.path'] == '/traced/error/John' &&
                        it.tags['http.status_code'] == 500 &&
                        it.tags['http.server']

            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/error/{name}' &&
                        it.tags['http.path'] == '/traced/error/John' &&
                        it.tags['http.status_code'] == 500 &&
                        it.tags['error'] == 'Internal Server Error' &&
                        it.tags['http.client']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/nestedError/{name}' &&
                        it.tags.containsKey('error') &&
                        it.tags['http.path'] == '/traced/nestedError/John' &&
                        it.tags['http.status_code'] == 500 &&
                        it.tags['http.server']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/nestedError/John' &&
                        it.tags['http.path'] == '/traced/nestedError/John' &&
                        it.tags['http.status_code'] == 500 &&
                        it.tags['error'] &&
                        it.tags['http.client']
            } != null

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test nested HTTP error tracing - blocking controller method'() {
        when:
        client.toBlocking().exchange('/traced/blocking/nestedError/John', String)

        then:
        HttpClientResponseException e = thrown()
        e != null

        conditions.eventually {
            reporter.spans.size() == 4

            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/error/{name}' &&
                        it.tags.containsKey('error') &&
                        it.tags['http.path'] == '/traced/blocking/error/John' &&
                        it.tags['http.status_code'] == 500 &&
                        it.tags['http.server']

            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/error/{name}' &&
                        it.tags['http.path'] == '/traced/blocking/error/John' &&
                        it.tags['http.status_code'] == 500 &&
                        it.tags['error'] == 'Internal Server Error' &&
                        it.tags['http.client']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/nestedError/{name}' &&
                        it.tags.containsKey('error') &&
                        it.tags['http.path'] == '/traced/blocking/nestedError/John' &&
                        it.tags['http.status_code'] == 500 &&
                        it.tags['http.server']
            } != null

            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/nestedError/John' &&
                        it.tags['http.path'] == '/traced/blocking/nestedError/John' &&
                        it.tags['http.status_code'] == 500 &&
                        it.tags['error'] &&
                        it.tags['http.client']
            } != null

            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test continue nested HTTP tracing - reactive'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nestedReactive/John', String)

        then:
        response.body() == '10'

        and: 'all spans are finished'
        conditions.eventually {
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test customising span name'() {
        when:
        client.toBlocking().exchange('/traced/customised/name', String)

        then:
        conditions.eventually {
            reporter.spans.any { it.operationName == 'custom name' }
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void 'test customising span name - blocking controller method'() {
        when:
        client.toBlocking().exchange('/traced/blocking/customised/name', String)

        then:
        conditions.eventually {
            reporter.spans.any { it.operationName == 'custom name' }
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    private long getJaegerMetric(String name, Map tags = [:]) {
        context.getBean(InMemoryMetricsFactory).getCounter('jaeger_tracer_' + name, tags)
    }

    private long getNrOfFinishedSpans() {
        getJaegerMetric('finished_spans')
    }

    private long getNrOfStartedSpans() {
        getJaegerMetric('started_spans', ['sampled': 'y'])
    }
}
