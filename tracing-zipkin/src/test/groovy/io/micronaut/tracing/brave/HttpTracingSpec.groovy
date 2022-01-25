package io.micronaut.tracing.brave

import brave.SpanCustomizer
import brave.propagation.StrictCurrentTraceContext
import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Specification
import zipkin2.Span
import zipkin2.Span.Kind

import static io.micronaut.http.MediaType.TEXT_PLAIN
import static zipkin2.Span.Kind.CLIENT
import static zipkin2.Span.Kind.SERVER

/**
 * @author graemerocher
 * @since 1.0
 */
class HttpTracingSpec extends Specification {

    @AutoCleanup
    private ApplicationContext context
    private TestReporter reporter
    private EmbeddedServer embeddedServer
    private HttpClient client

    void 'test basic HTTP tracing'() {
        when:
        buildContext()
        HttpResponse<String> response = client.toBlocking().exchange('/traced/hello/John', String)

        then:
        response
        reporter.spans.size() == 2
        reporter.spans[0].tags()['foo'] == 'bar'
        reporter.spans[0].tags()['http.path'] == '/traced/hello/John'
        reporter.spans[0].name() == 'get /traced/hello/{name}'
    }

    void 'test basic HTTP trace filtering'() {
        when:
        buildContext('tracing.exclusions[0]': '/traced/h.*')
        HttpResponse<String> response = client.toBlocking().exchange('/traced/hello/John', String)

        then:
        response
        reporter.spans.empty
    }

    void 'test RxJava HTTP tracing'() {
        when:
        buildContext()
        HttpResponse<String> response = client.toBlocking().exchange('/traced/rxjava/John', String)

        then:
        response
        reporter.spans.size() == 2

        reporter.spans[0].tags()['http.path'] == '/traced/rxjava/John'
        reporter.spans[0].name() == 'get /traced/rxjava/{name}'
        reporter.spans[0].id() == reporter.spans[1].id()
        reporter.spans[0].kind() == SERVER
        reporter.spans[0].tags()['foo'] == 'bar'

        reporter.spans[1].tags()['http.path'] == '/traced/rxjava/John'
        reporter.spans[1].id() == reporter.spans[1].id()
        reporter.spans[1].kind() == CLIENT

        when: 'An publishOn call is used'
        response = client.toBlocking().exchange('/traced/rxjava/observe', String)

        then: 'The response is correct'
        response.body() == 'hello'
    }

    void 'test RxJava HTTP tracing with exclusions'() {
        when:
        buildContext('tracing.exclusions[0]': '/traced/rxjava/John')
        HttpResponse<String> response = client.toBlocking().exchange('/traced/rxjava/John', String)

        then:
        response
        reporter.spans.empty

        when: 'An publishOn call is used'
        response = client.toBlocking().exchange('/traced/rxjava/observe', String)

        then: 'The response is correct'
        response.body() == 'hello'
        reporter.spans.size() == 2

        reporter.spans[0].tags()['http.path'] == '/traced/rxjava/observe'
        reporter.spans[0].name() == 'get /traced/rxjava/observe'
        reporter.spans[0].id() == reporter.spans[1].id()
        reporter.spans[0].kind() == SERVER

        reporter.spans[1].tags()['http.path'] == '/traced/rxjava/observe'
        reporter.spans[1].name() == 'get'
        reporter.spans[1].kind() == CLIENT
    }

    void 'test basic HTTP trace error'() {
        when:
        buildContext()
        client.toBlocking().exchange('/traced/error/John', String)

        then:
        HttpClientResponseException e = thrown()
        e.response
        reporter.spans.size() == 2

        reporter.spans[0].tags()['http.path'] == '/traced/error/John'
        reporter.spans[0].tags()['http.status_code'] == '500'
        reporter.spans[0].tags()['http.method'] == 'GET'
        reporter.spans[0].tags()['error'] == 'bad'
        reporter.spans[0].name() == 'get /traced/error/{name}'

        reporter.spans[1].tags()['http.path'] == '/traced/error/John'
        reporter.spans[1].tags()['http.status_code'] == '500'
        reporter.spans[1].tags()['http.method'] == 'GET'
        reporter.spans[1].tags()['error'] == 'Internal Server Error'
        reporter.spans[1].name() == 'get'
    }

    void 'test nested HTTP tracing'() {
        when:
        buildContext()
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)

        then:
        response
        reporter.spans.size() == 4

        reporter.spans[0].tags()['foo'] == 'bar'
        reporter.spans[0].tags()['http.path'] == '/traced/hello/John'
        reporter.spans[0].name() == 'get /traced/hello/{name}'
        reporter.spans[0].kind() == SERVER

        reporter.spans[1].tags()['http.path'] == '/traced/hello/John'
        reporter.spans[1].name() == 'get /traced/hello/{name}'
        reporter.spans[1].kind() == CLIENT

        reporter.spans[2].name() == 'get /traced/nested/{name}'
        reporter.spans[2].kind() == SERVER
        reporter.spans[2].tags()['http.method'] == 'GET'
        reporter.spans[2].tags()['http.path'] == '/traced/nested/John'

        reporter.spans[3].tags()['foo'] == null
        reporter.spans[3].tags()['http.path'] == '/traced/nested/John'
        reporter.spans[3].name() == 'get'
        reporter.spans[3].kind() == CLIENT
    }

    void 'test nested HTTP tracing with server without tracing'() {
        given:
        ApplicationContext appWithoutTracing = ApplicationContext.builder().start()
        EmbeddedServer embeddedServerWithoutTracing = appWithoutTracing.getBean(EmbeddedServer).start()

        ApplicationContext context = ApplicationContext.builder(
                'tracing.zipkin.enabled': true,
                'tracing.zipkin.sampler.probability': 1,
                'micronaut.http.services.not-traced-client.urls[0]': "http://localhost:${embeddedServerWithoutTracing.port}",
        )
                .singletons(new StrictCurrentTraceContext(), new TestReporter())
                .start()

        TestReporter reporter = context.getBean(TestReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested-not-traced/John', String)

        then:
        response
        reporter.spans.size() == 3

        reporter.spans[0].tags()['http.path'] == '/not-traced/hello/John'
        reporter.spans[0].name() == 'get /not-traced/hello/{name}'
        reporter.spans[0].kind() == CLIENT
        reporter.spans[0].remoteEndpoint().serviceName() == 'not-traced-client'

        reporter.spans[1].name() == 'get /traced/nested-not-traced/{name}'
        reporter.spans[1].kind() == SERVER
        reporter.spans[1].tags()['http.method'] == 'GET'
        reporter.spans[1].tags()['http.path'] == '/traced/nested-not-traced/John'

        reporter.spans[2].tags()['foo'] == null
        reporter.spans[2].tags()['http.path'] == '/traced/nested-not-traced/John'
        reporter.spans[2].name() == 'get'
        reporter.spans[2].kind() == CLIENT

        cleanup:
        client.close()
        context.close()
        appWithoutTracing.close()
    }

    void 'test nested HTTP tracing with server without tracing and uri exclusions'() {
        given:
        ApplicationContext appWithoutTracing = ApplicationContext.builder().start()
        EmbeddedServer embeddedServerWithoutTracing = appWithoutTracing.getBean(EmbeddedServer).start()

        ApplicationContext context = ApplicationContext.builder(
                'tracing.zipkin.enabled': true,
                'tracing.zipkin.sampler.probability': 1,
                'tracing.exclusions[0]': '.*hello.*',
                'micronaut.http.services.not-traced-client.urls[0]': "http://localhost:${embeddedServerWithoutTracing.port}",
        )
                .singletons(new StrictCurrentTraceContext(), new TestReporter())
                .start()

        TestReporter reporter = context.getBean(TestReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested-not-traced/John', String)

        then:
        response
        reporter.spans.size() == 2

        reporter.spans[0].name() == 'get /traced/nested-not-traced/{name}'
        reporter.spans[0].kind() == SERVER
        reporter.spans[0].tags()['http.method'] == 'GET'
        reporter.spans[0].tags()['http.path'] == '/traced/nested-not-traced/John'

        reporter.spans[1].tags()['foo'] == null
        reporter.spans[1].tags()['http.path'] == '/traced/nested-not-traced/John'
        reporter.spans[1].name() == 'get'
        reporter.spans[1].kind() == CLIENT

        cleanup:
        client.close()
        context.close()
        appWithoutTracing.close()
    }

    void 'test nested HTTP error tracing'() {
        when:
        buildContext()
        client.toBlocking().exchange('/traced/nestedError/John', String)

        then:
        HttpClientResponseException e = thrown()
        reporter.spans.size() == 4

        assertSpan(reporter.spans[0],
                'get /traced/error/{name}',
                'bad',
                '/traced/error/John',
                SERVER)

        assertSpan(reporter.spans[1],
                'get /traced/error/{name}',
                'Internal Server Error',
                '/traced/error/John',
                CLIENT)

        assertSpan(reporter.spans[2],
                'get /traced/nestederror/{name}',
                'Internal Server Error',
                '/traced/nestedError/John',
                SERVER)

        assertSpan(reporter.spans[3],
                'get',
                'Internal Server Error',
                '/traced/nestedError/John',
                CLIENT)
    }

    private boolean assertSpan(Span span, String name, String error, String path, Kind kind) {
        return span.tags()['http.path'] == path &&
                span.tags()['error'] == error &&
                span.name() == name &&
                span.kind() == kind
    }

    private void buildContext(Map<String, ?> extraConfig = [:]) {
        context = ApplicationContext
                .builder([
                        'tracing.zipkin.enabled'            : true,
                        'tracing.zipkin.sampler.probability': 1
                ] + extraConfig)
                .singletons(new StrictCurrentTraceContext(), new TestReporter())
                .start()
        reporter = context.getBean(TestReporter)
        embeddedServer = context.getBean(EmbeddedServer).start()
        client = context.createBean(HttpClient, embeddedServer.URL)
    }

    @Controller('/traced')
    static class TracedController {

        @Inject
        SpanCustomizer spanCustomizer

        @Inject
        TracedClient tracedClient

        @Inject
        NotTracedEndpointClient notTracedEndpointClient

        @Get('/hello/{name}')
        String hello(String name) {
            spanCustomizer.tag('foo', 'bar')
            return name
        }

        @Get(value = '/rxjava/observe', produces = TEXT_PLAIN)
        @SingleResult
        Publisher<String> index() {
            return Mono.just('hello').publishOn(Schedulers.boundedElastic()).map({ r ->
                if (!ServerRequestContext.currentRequest().isPresent()) {
                    throw new RuntimeException('fail')
                }
                return r
            })
        }

        @Get('/rxjava/{name}')
        @SingleResult
        Publisher<String> rxjava(String name) {
            Mono.fromCallable({ ->
                spanCustomizer.tag('foo', 'bar')
                return name
            }).subscribeOn(Schedulers.boundedElastic())
        }

        @Get('/error/{name}')
        String error(String name) {
            throw new RuntimeException('bad')
        }

        @Get('/nested/{name}')
        String nested(String name) {
            tracedClient.hello(name)
        }

        @Get('/nested-not-traced/{name}')
        String nestedNotTraced(String name) {
            notTracedEndpointClient.hello(name)
        }

        @Get('/nestedError/{name}')
        String nestedError(String name) {
            tracedClient.error(name)
        }
    }

    @Client('/traced')
    static interface TracedClient {
        @Get('/hello/{name}')
        String hello(String name)

        @Get('/error/{name}')
        String error(String name)
    }

    @Controller('/not-traced')
    static class NotTracedController {

        @Get('/hello/{name}')
        String hello(String name) {
            return name
        }
    }

    @Client(id = 'not-traced-client')
    static interface NotTracedEndpointClient {
        @Get('/not-traced/hello/{name}')
        String hello(String name)
    }
}
