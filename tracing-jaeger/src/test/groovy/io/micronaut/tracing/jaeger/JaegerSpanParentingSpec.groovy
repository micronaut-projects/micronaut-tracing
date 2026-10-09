package io.micronaut.tracing.jaeger

import io.jaegertracing.internal.JaegerSpan
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory
import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.tracing.annotation.NewSpan
import io.opentracing.Tracer
import io.reactivex.rxjava3.core.Flowable
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService

class JaegerSpanParentingSpec extends Specification {

    static final String SPEC_NAME = 'JaegerSpanParentingSpec'

    @AutoCleanup
    ApplicationContext context
    EmbeddedServer embeddedServer
    @AutoCleanup
    HttpClient client
    InMemoryReporter reporter
    PollingConditions conditions = new PollingConditions(timeout: 10)

    void setup() {
        context = ApplicationContext
                .builder('tracing.jaeger.enabled': true,
                         'tracing.jaeger.sampler.probability': 1,
                         'spec.name': SPEC_NAME)
                .singletons(new InMemoryReporter(), new InMemoryMetricsFactory())
                .start()
        embeddedServer = context.getBean(EmbeddedServer).start()
        client = context.createBean(HttpClient, embeddedServer.URL)
        reporter = context.getBean(InMemoryReporter)
        IoSwitchFilter.seen.clear()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/130')
    @Unroll
    void 'issue 130 - @NewSpan method called from #method controller is a child of the server span'() {
        when:
        String body = client.toBlocking().retrieve(request, String)

        then:
        body.startsWith('Hello')
        conditions.eventually {
            JaegerSpan serverSpan = reporter.spans.find { it.tags['http.server'] == true && it.tags['http.path'] == path }
            JaegerSpan messageSpan = reporter.spans.find { it.operationName.contains('message') }
            assert serverSpan != null
            assert messageSpan != null
            assert messageSpan.context().traceId == serverSpan.context().traceId
            assert messageSpan.context().parentId == serverSpan.context().spanId
        }

        where:
        method        | path          | request
        'GET'         | '/issue130/foo' | HttpRequest.GET('/issue130/foo')
        'POST @Body'  | '/issue130/bar' | HttpRequest.POST('/issue130/bar', 'John').contentType(MediaType.TEXT_PLAIN)
        'POST @Body (JSON)' | '/issue130/json' | HttpRequest.POST('/issue130/json', [name: 'John'])
    }

    @Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/935')
    @Unroll
    void 'issue 935 - active span is available in a server filter after switching to #mode'() {
        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/issue935/hello").header('X-Mode', mode), String)

        then:
        conditions.eventually {
            JaegerSpan serverSpan = reporter.spans.find { it.tags['http.server'] == true && it.tags['http.path'] == '/issue935/hello' }
            JaegerSpan childSpan = reporter.spans.find { it.operationName.contains('work') }
            assert serverSpan != null
            assert childSpan != null
            String serverTraceId = serverSpan.context().toTraceId()

            assert IoSwitchFilter.seen.size() == 1
            Map seen = IoSwitchFilter.seen[0]
            assert seen.thread != null && !seen.thread.contains('event-loop')
            assert seen.traceId == serverTraceId: "tracer.activeSpan() in filter after thread switch on ${seen.thread}"
            assert body == serverTraceId: 'tracer.activeSpan() in controller after filter thread switch'
            assert childSpan.context().traceId == serverSpan.context().traceId
            assert childSpan.context().parentId == serverSpan.context().spanId
        }

        where:
        mode << ['subscribeOn-boundedElastic', 'subscribeOn-io-executor', 'publishOn-io-executor', 'rxjava3-subscribeOn-io']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/issue130')
    static class FooController {

        @Get('/foo')
        String foo() {
            message()
        }

        @Post(value = '/bar', consumes = MediaType.TEXT_PLAIN)
        String bar(@Body String name) {
            message() + name
        }

        @Post('/json')
        String json(@Body Map<String, String> body) {
            message() + body.name
        }

        @NewSpan
        String message() {
            'Hello'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Filter('/issue935/**')
    static class IoSwitchFilter implements HttpServerFilter {

        static final List<Map> seen = new CopyOnWriteArrayList<>()

        @Inject
        Tracer tracer

        @Inject
        @Named(TaskExecutors.IO)
        ExecutorService ioExecutor

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            String mode = request.headers.get('X-Mode')
            final Mono<Boolean> record = Mono.fromCallable { ->
                seen << [thread: Thread.currentThread().name, traceId: tracer.activeSpan()?.context()?.toTraceId()]
                true
            }
            Mono<Boolean> check = record
            switch (mode) {
                case 'subscribeOn-boundedElastic':
                    check = check.subscribeOn(Schedulers.boundedElastic())
                    break
                case 'subscribeOn-io-executor':
                    check = check.subscribeOn(Schedulers.fromExecutorService(ioExecutor))
                    break
                case 'publishOn-io-executor':
                    check = Mono.just(true).publishOn(Schedulers.fromExecutorService(ioExecutor)).flatMap { record }
                    break
                case 'rxjava3-subscribeOn-io':
                    // the filter from the issue, ported from RxJava 2 to RxJava 3
                    return Flowable.fromCallable { -> record.block() }
                            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                            .switchMap { Flowable.fromPublisher(chain.proceed(request)) }
            }
            return check.flatMapMany { chain.proceed(request) }
        }

        @Override
        int getOrder() {
            LOWEST_PRECEDENCE
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/issue935')
    static class Issue935Controller {

        @Inject
        Tracer tracer

        @Inject
        Issue935Service service

        @Get('/hello')
        String hello() {
            service.work()
            tracer.activeSpan()?.context()?.toTraceId()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class Issue935Service {

        @NewSpan
        String work() {
            'done'
        }
    }
}
