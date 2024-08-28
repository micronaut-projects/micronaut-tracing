package io.micronaut.tracing.opentelemetry.exporter.zipkin

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.zipkin.http.client.HttpClientSender
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Retry
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import zipkin2.reporter.Sender

import static io.micronaut.http.HttpStatus.OK
import static io.micronaut.http.MediaType.TEXT_PLAIN
import static zipkin2.Span.Kind.CLIENT
import static zipkin2.Span.Kind.SERVER


@Retry
class HttpClientSenderSpec extends Specification {

    void 'test http client sender bean initialization with instrumented threads'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'otel.exporter.zipkin.url': HttpClientSender.Builder.DEFAULT_SERVER_URL
        )

        when:
        Sender httpClientSender = context.getBean(Sender)

        then:
        httpClientSender instanceof HttpClientSender

        cleanup:
        httpClientSender.close()
        context.close()
    }

    void 'test http client sender receives spans'() {
        given:
        EmbeddedServer zipkinServer = ApplicationContext.run(
                EmbeddedServer,
                ['micronaut.server.port': -1]
        )
        SpanController spanController = zipkinServer.applicationContext.getBean(SpanController)

        ApplicationContext context = ApplicationContext.run(
                'otel.exporter.zipkin.url': zipkinServer.URL.toString(),
                'otel.exporter.zipkin.message-max-bytes': 100000,
                'otel.exporter.zipkin.encoding': 'JSON',
                'otel.exporter.zipkin.compression-enabled': false,
        )

        when:
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)
        PollingConditions conditions = new PollingConditions(timeout: 10)
        StartedListener listener = zipkinServer.applicationContext.getBean(StartedListener)
        Sender sender = context.getBean(Sender)

        then:
        conditions.eventually {
            listener.started
        }

        when: 'Requests are executed'
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)

        then: 'spans are received'
        conditions.eventually {
            response.status() == OK
            spanController.receivedSpans.size() == 4

            spanController.receivedSpans[0].tags.get('foo') == 'bar'
            spanController.receivedSpans[0].tags.get(UrlAttributes.URL_PATH.key) == '/traced/hello/John'
            spanController.receivedSpans[0].name == 'get /traced/hello/{name}'
            spanController.receivedSpans[0].kind == SERVER.name()

            spanController.receivedSpans[1].tags.get(UrlAttributes.URL_FULL.key).contains("/traced/hello/John")
            spanController.receivedSpans[1].name == 'get'
            spanController.receivedSpans[1].kind == CLIENT.name()

            spanController.receivedSpans[2].name == 'get /traced/nested/{name}'
            spanController.receivedSpans[2].kind == SERVER.name()
            spanController.receivedSpans[2].tags.get(HttpAttributes.HTTP_REQUEST_METHOD.key) == 'GET'
            spanController.receivedSpans[2].tags.get(UrlAttributes.URL_PATH.key) == '/traced/nested/John'

            spanController.receivedSpans[3].tags.get('foo') == null
            spanController.receivedSpans[3].tags.get(UrlAttributes.URL_FULL.key).contains('/traced/nested/John')
            spanController.receivedSpans[3].name == 'get'
            spanController.receivedSpans[3].kind == CLIENT.name()
        }

        when: 'Sender coverage test'
        Sender s = context.getBean(Sender)

        then:
        s.check()

        cleanup:
        client.close()
        context.close()
        zipkinServer.close()
    }

    void 'test http client sender receives spans with custom path'() {
        given:
        EmbeddedServer zipkinServer = ApplicationContext.run(
                EmbeddedServer,
                ['micronaut.server.port': -1]
        )
        ApplicationContext context = ApplicationContext.run(
                'otel.exporter.zipkin.url': zipkinServer.URL.toString(),
                'otel.exporter.zipkin.path': '/custom/path/spans'
        )

        when:
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)

        PollingConditions conditions = new PollingConditions(timeout: 10)
        CustomPathSpanController customPathSpanController = zipkinServer.applicationContext.getBean(CustomPathSpanController)
        StartedListener listener = zipkinServer.applicationContext.getBean(StartedListener)

        then:
        conditions.eventually {
            listener.started
        }

        when: 'Requests are executed'
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)

        then: 'spans are received'
        conditions.eventually {
            response.status() == OK
            customPathSpanController.receivedSpans.size() == 4

            customPathSpanController.receivedSpans[0].tags.get('foo') == 'bar'
            customPathSpanController.receivedSpans[0].tags.get(UrlAttributes.URL_PATH.key) == '/traced/hello/John'
            customPathSpanController.receivedSpans[0].name == 'get /traced/hello/{name}'
            customPathSpanController.receivedSpans[0].kind == SERVER.name()

            customPathSpanController.receivedSpans[1].tags.get(UrlAttributes.URL_FULL.key).contains("/traced/hello/John")
            customPathSpanController.receivedSpans[1].name == 'get'
            customPathSpanController.receivedSpans[1].kind == CLIENT.name()

            customPathSpanController.receivedSpans[2].name == 'get /traced/nested/{name}'
            customPathSpanController.receivedSpans[2].kind == SERVER.name()
            customPathSpanController.receivedSpans[2].tags.get(HttpAttributes.HTTP_REQUEST_METHOD.key) == 'GET'
            customPathSpanController.receivedSpans[2].tags.get(UrlAttributes.URL_PATH.key) == '/traced/nested/John'

            customPathSpanController.receivedSpans[3].tags.get('foo') == null
            customPathSpanController.receivedSpans[3].tags.get(UrlAttributes.URL_FULL.key).contains("/traced/nested/John")
            customPathSpanController.receivedSpans[3].name == 'get'
            customPathSpanController.receivedSpans[3].kind == CLIENT.name()
        }

        cleanup:
        client.close()
        context.close()
        zipkinServer.close()
    }

    @Controller('/api/v2')
    static class SpanController {
        List<Map> receivedSpans = []

        @Post('/spans')
        @SingleResult
        Publisher<HttpResponse> spans(@Body Publisher<Map> spans) {
            Flux.from(spans)
                    .collectList()
                    .map({ List list ->
                        receivedSpans.addAll(list)
                        HttpResponse.ok()
                    })
        }
    }

    @Controller('/custom/path')
    static class CustomPathSpanController {
        List<Map> receivedSpans = []

        @Post('/spans')
        @SingleResult
        Publisher<HttpResponse> spans(@Body Publisher<Map> spans) {
            Flux.from(spans).collectList().map({ List list ->
                receivedSpans.addAll(list)
                HttpResponse.ok()
            })
        }
    }

    @Singleton
    static class StartedListener implements ApplicationEventListener<ServerStartupEvent> {
        boolean started

        @Override
        void onApplicationEvent(ServerStartupEvent event) {
            started = true
        }
    }

    @Controller('/traced')
    static class TracedController {

        @Inject
        TracedClient tracedClient

        @Inject
        NotTracedEndpointClient notTracedEndpointClient

        @Get('/hello/{name}')
        String hello(String name) {
            Span span = Span.current();
            span.setAttribute("foo", "bar")
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

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get('/nested/{name}')
        String nested(String name) {
            tracedClient.hello(name)
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get('/nested-not-traced/{name}')
        String nestedNotTraced(String name) {
            notTracedEndpointClient.hello(name)
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
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
