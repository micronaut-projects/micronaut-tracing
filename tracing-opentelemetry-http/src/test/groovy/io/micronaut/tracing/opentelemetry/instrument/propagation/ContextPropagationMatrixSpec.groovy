package io.micronaut.tracing.opentelemetry.instrument.propagation

import groovy.json.JsonSlurper
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.Async
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.opentelemetry.test.TestSpans
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * The trace context of a request across the execution models supported by Micronaut: virtual threads,
 * {@code @ExecuteOn}, {@code @Async}, executors, {@code CompletableFuture} and Reactor schedulers.
 *
 * <p>Each endpoint reports the span that is current at each step. The spec checks that the trace and the parent
 * of every span are right and that the context does not leak onto the worker thread afterwards. The executors
 * and schedulers whose threads are reused have a single thread so that the leak check runs on the thread that
 * did the work.</p>
 *
 * <p>The executor beans are instrumented by {@code micronaut-context-propagation} (on the test classpath), which
 * {@code @Async} and executors injected as beans rely on.</p>
 */
class ContextPropagationMatrixSpec extends Specification {

    static final String SPEC_NAME = 'ContextPropagationMatrixSpec'

    @Shared
    @AutoCleanup
    ApplicationContext context

    @Shared
    EmbeddedServer embeddedServer

    @Shared
    TestSpans testSpans

    Map<String, Object> configuration() {
        [
            'spec.name'                                : SPEC_NAME,
            'otel.register.global'                     : false,
            'micronaut.application.name'               : 'test-app',
            'micronaut.http.client.read-timeout'       : '30s',
            // single threads: the leak checks run on the thread that did the work
            'micronaut.executors.io.type'              : 'fixed',
            'micronaut.executors.io.n-threads'         : 1,
            'micronaut.executors.scheduled.core-pool-size': 1,
        ]
    }

    void setupSpec() {
        context = ApplicationContext.run(configuration())
        embeddedServer = context.getBean(EmbeddedServer).start()
        testSpans = context.getBean(TestSpans)
    }

    void setup() {
        testSpans.reset()
    }

    // ---- virtual threads and @ExecuteOn ----

    @Unroll
    void '@ExecuteOn(#executor) controller, @NewSpan method and HTTP client call on a virtual thread'() {
        when:
        Map body = get(path)

        then:
        body.controller_virtual
        body.newspan_virtual

        and:
        Chain chain = awaitChain("GET $path", 'newSpanWithClient')
        body.controller_trace == chain.server.traceId
        body.controller_span == chain.server.spanId
        body.newspan_span == chain.newSpan.spanId
        body.downstream_trace == chain.server.traceId

        where:
        executor   | path
        'VIRTUAL'  | '/matrix/virtual'
        'BLOCKING' | '/matrix/blocking'
    }

    void 'virtual thread executor used manually with PropagatedContext.wrap'() {
        when:
        Map body = get('/matrix/virtual-wrap')

        then:
        body.task_virtual
        body.task_valid
        body.after_virtual

        and:
        Chain chain = awaitChain('GET /matrix/virtual-wrap', 'newSpanWithClient')
        body.task_span == chain.server.spanId
        body.newspan_span == chain.newSpan.spanId
        body.downstream_trace == chain.server.traceId

        and: 'the virtual thread is left without the context once the wrapped task returned'
        !body.after_valid
    }

    void 'virtual thread executor bean, instrumented by micronaut-context-propagation'() {
        when:
        Map body = get('/matrix/virtual-bean')

        then:
        body.task_virtual

        and:
        Chain chain = awaitChain('GET /matrix/virtual-bean', 'newSpanWithClient')
        body.task_span == chain.server.spanId
        body.newspan_span == chain.newSpan.spanId
        body.downstream_trace == chain.server.traceId
    }

    // ---- @Async and CompletableFuture ----

    @Unroll
    void '@Async #kind method'() {
        when:
        Map body = get(path)

        then:
        Chain chain = awaitChain("GET $path", 'newSpan', false)
        body.task_span == chain.server.spanId
        body.newspan_span == chain.newSpan.spanId
        body.task_thread != body.controller_thread

        and: 'the single scheduled thread is left without the context'
        !leftOver(context.getBean(ExecutorService, Qualifiers.byName(TaskExecutors.SCHEDULED))).leak_valid

        where:
        kind                | path
        'void'              | '/matrix/async-void'
        'CompletableFuture' | '/matrix/async-future'
    }

    void 'CompletableFuture.supplyAsync with the instrumented IO executor'() {
        when:
        Map body = get('/matrix/supply-async/io')

        then:
        Chain chain = awaitChain('GET /matrix/supply-async/io', 'newSpan', false)
        body.task_span == chain.server.spanId
        body.newspan_span == chain.newSpan.spanId

        and: 'the single IO thread is left without the context'
        !leftOver(context.getBean(ExecutorService, Qualifiers.byName(TaskExecutors.IO))).leak_valid
    }

    void 'CompletableFuture.supplyAsync on the common pool loses the context unless the task is wrapped'() {
        when: 'the task is not wrapped'
        Map body = get('/matrix/supply-async/common')

        then: 'the task has no current span and its @NewSpan starts a new trace'
        !body.task_valid
        testSpans.awaitSpans(2)
        SpanData server = testSpans.spanNamed('GET /matrix/supply-async/{executor}')
        SpanData newSpan = testSpans.spanNamed('MatrixService.newSpan')
        newSpan.traceId != server.traceId
        newSpan.parentSpanId == SpanId.invalid

        when: 'the task is wrapped with PropagatedContext.wrapCurrent'
        testSpans.reset()
        body = get('/matrix/supply-async/common-wrapped')

        then:
        Chain chain = awaitChain('GET /matrix/supply-async/{executor}', 'newSpan', false)
        body.task_span == chain.server.spanId
        body.newspan_span == chain.newSpan.spanId
    }

    // ---- Reactor ----

    @Unroll
    void 'Reactor #operator(#scheduler) in a controller'() {
        when:
        Map body = get("/matrix/reactor/$operator/$scheduler")

        then: 'the HTTP client call after the hop is a child of the server span, through the Reactor context'
        List<SpanData> spans = testSpans.awaitSpans(3)
        SpanData server = testSpans.spanNamed('GET /matrix/reactor/{operator}/{scheduler}')
        SpanData client = single(spans, SpanKind.CLIENT)
        client.parentSpanId == server.spanId
        downstreamServer().parentSpanId == client.spanId
        body.downstream_trace == server.traceId

        and: 'the scheduler thread is left without the context'
        !leftOver(scheduler(scheduler)).leak_valid

        where:
        [operator, scheduler] << [['publishOn', 'subscribeOn'], ['boundedElastic', 'parallel']].combinations()
    }

    @Unroll
    void 'Reactor #operator(#scheduler) inside a reactive @NewSpan method'() {
        when:
        Map body = get("/matrix/reactor-newspan/$operator/$scheduler")

        then: 'the HTTP client call after the hop is a child of the span of the method'
        List<SpanData> spans = testSpans.awaitSpans(4)
        SpanData server = testSpans.spanNamed('GET /matrix/reactor-newspan/{operator}/{scheduler}')
        SpanData newSpan = testSpans.spanNamed('MatrixService.reactiveNewSpan')
        SpanData client = single(spans, SpanKind.CLIENT)
        newSpan.parentSpanId == server.spanId
        client.parentSpanId == newSpan.spanId
        downstreamServer().parentSpanId == client.spanId
        body.downstream_trace == server.traceId

        and: 'the scheduler thread is left without the context'
        !leftOver(scheduler(scheduler)).leak_valid

        where:
        [operator, scheduler] << [['publishOn', 'subscribeOn'], ['boundedElastic', 'parallel']].combinations()
    }

    void 'Flux.flatMap with concurrent HTTP client calls on the parallel scheduler'() {
        when:
        Map body = get('/matrix/reactor-flatmap')

        then:
        testSpans.awaitSpans(9)
        SpanData server = testSpans.spanNamed('GET /matrix/reactor-flatmap')
        List<SpanData> clients = testSpans.spansOfKind(SpanKind.CLIENT)
        clients.size() == 4
        clients.every { it.traceId == server.traceId && it.parentSpanId == server.spanId }
        testSpans.spansNamed('GET /matrix/downstream').every { downstream ->
            clients.any { it.spanId == downstream.parentSpanId }
        }
        (body.downstream_traces as List).every { it == server.traceId }
    }

    // ---- helpers ----

    static class Chain {
        SpanData server
        SpanData newSpan
    }

    /**
     * Waits for the server span, the @NewSpan span and, if {@code withClient}, the client and downstream server
     * spans, and checks that each is the child of the previous one.
     */
    Chain awaitChain(String serverSpanName, String newSpanMethod, boolean withClient = true) {
        List<SpanData> spans = testSpans.awaitSpans(withClient ? 4 : 2)
        SpanData server = testSpans.spanNamed(serverSpanName)
        SpanData newSpan = testSpans.spanNamed("MatrixService.$newSpanMethod")
        assert server.parentSpanId == SpanId.invalid
        assert newSpan.traceId == server.traceId
        assert newSpan.parentSpanId == server.spanId
        if (withClient) {
            SpanData client = single(spans, SpanKind.CLIENT)
            assert client.parentSpanId == newSpan.spanId
            assert downstreamServer().parentSpanId == client.spanId
        }
        new Chain(server: server, newSpan: newSpan)
    }

    SpanData downstreamServer() {
        testSpans.spanNamed('GET /matrix/downstream')
    }

    static SpanData single(List<SpanData> spans, SpanKind kind) {
        List<SpanData> ofKind = spans.findAll { it.kind == kind }
        assert ofKind.size() == 1
        ofKind[0]
    }

    Scheduler scheduler(String name) {
        context.getBean(Scheduler, Qualifiers.byName("matrix-$name"))
    }

    static Map leftOver(ExecutorService executor) {
        executor.submit({ Ids.of('leak') } as Callable<Map>).get(10, TimeUnit.SECONDS)
    }

    static Map leftOver(Scheduler scheduler) {
        Mono.fromCallable { Ids.of('leak') }.subscribeOn(scheduler).block()
    }

    Map get(String path) {
        new JsonSlurper().parseText(new URL(embeddedServer.URL, path).text) as Map
    }

    /**
     * The span current on the calling thread.
     */
    static class Ids {
        static Map<String, Object> of(String prefix) {
            SpanContext spanContext = Span.current().spanContext
            Thread thread = Thread.currentThread()
            [
                ("${prefix}_trace".toString())  : spanContext.traceId,
                ("${prefix}_span".toString())   : spanContext.spanId,
                ("${prefix}_valid".toString())  : spanContext.valid,
                ("${prefix}_thread".toString()) : thread.toString(),
                ("${prefix}_virtual".toString()): thread.virtual,
            ]
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class MatrixFactory {

        @Singleton
        @Named('matrix-virtual')
        @Bean(preDestroy = 'shutdown')
        ExecutorService virtualExecutor() {
            Executors.newVirtualThreadPerTaskExecutor()
        }

        @Singleton
        @Named('matrix-boundedElastic')
        @Bean(preDestroy = 'dispose')
        Scheduler boundedElastic() {
            Schedulers.newBoundedElastic(1, 100, 'matrix-bounded-elastic')
        }

        @Singleton
        @Named('matrix-parallel')
        @Bean(preDestroy = 'dispose')
        Scheduler parallel() {
            Schedulers.newParallel('matrix-parallel', 1)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Client('/matrix')
    static interface MatrixClient {

        @Get('/downstream')
        String downstream()

        @Get('/downstream')
        @SingleResult
        Mono<String> downstreamReactive()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class MatrixService {

        @Inject
        MatrixClient client

        @NewSpan
        Map<String, Object> newSpan() {
            Ids.of('newspan')
        }

        @NewSpan
        Map<String, Object> newSpanWithClient() {
            Ids.of('newspan') + [downstream_trace: client.downstream()]
        }

        @NewSpan
        Mono<Map<String, Object>> reactiveNewSpan(String operator, Scheduler scheduler) {
            reactive(operator, scheduler)
        }

        Mono<Map<String, Object>> reactive(String operator, Scheduler scheduler) {
            Mono<Map<String, Object>> before = Mono.fromCallable { Ids.of('before') }
            Mono<Map<String, Object>> hop = operator == 'publishOn' ? before.publishOn(scheduler) : before.subscribeOn(scheduler)
            hop.map { it + Ids.of('hop') }
                .flatMap { Map<String, Object> ids ->
                    client.downstreamReactive().map { String trace -> ids + [downstream_trace: trace] }
                }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class MatrixAsyncService {

        @Inject
        MatrixService service

        @Async
        void asyncVoid(CompletableFuture<Map<String, Object>> result) {
            try {
                result.complete(Ids.of('task') + service.newSpan())
            } catch (Throwable e) {
                result.completeExceptionally(e)
            }
        }

        @Async
        CompletableFuture<Map<String, Object>> asyncFuture() {
            CompletableFuture.completedFuture(Ids.of('task') + service.newSpan())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/matrix')
    static class MatrixController {

        @Inject
        MatrixService service

        @Inject
        MatrixAsyncService asyncService

        @Inject
        MatrixClient client

        @Inject
        @Named(TaskExecutors.IO)
        ExecutorService io

        @Inject
        @Named('matrix-virtual')
        ExecutorService virtualBean

        @Inject
        @Named('matrix-boundedElastic')
        Scheduler boundedElastic

        @Inject
        @Named('matrix-parallel')
        Scheduler parallel

        /** Not a bean: never instrumented. */
        private final ExecutorService plainVirtual = Executors.newVirtualThreadPerTaskExecutor()

        @Get('/downstream')
        String downstream() {
            Span.current().spanContext.traceId
        }

        @ExecuteOn(TaskExecutors.VIRTUAL)
        @Get('/virtual')
        Map<String, Object> virtual() {
            Ids.of('controller') + service.newSpanWithClient()
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get('/blocking')
        Map<String, Object> blocking() {
            Ids.of('controller') + service.newSpanWithClient()
        }

        @Get('/virtual-wrap')
        CompletableFuture<Map<String, Object>> virtualWrap() {
            Supplier<Map<String, Object>> task = PropagatedContext.wrapCurrent(
                { Ids.of('task') + service.newSpanWithClient() } as Supplier<Map<String, Object>>)
            CompletableFuture.supplyAsync({ task.get() + Ids.of('after') } as Supplier<Map<String, Object>>, plainVirtual)
        }

        @Get('/virtual-bean')
        CompletableFuture<Map<String, Object>> virtualBean() {
            CompletableFuture.supplyAsync({ Ids.of('task') + service.newSpanWithClient() } as Supplier<Map<String, Object>>, virtualBean)
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get('/async-void')
        Map<String, Object> asyncVoid() {
            CompletableFuture<Map<String, Object>> result = new CompletableFuture<>()
            asyncService.asyncVoid(result)
            Ids.of('controller') + result.get(10, TimeUnit.SECONDS)
        }

        @Get('/async-future')
        CompletableFuture<Map<String, Object>> asyncFuture() {
            Map<String, Object> controller = Ids.of('controller')
            asyncService.asyncFuture().thenApply { controller + it }
        }

        @Get('/supply-async/{executor}')
        CompletableFuture<Map<String, Object>> supplyAsync(String executor) {
            Supplier<Map<String, Object>> task = { Ids.of('task') + service.newSpan() } as Supplier<Map<String, Object>>
            switch (executor) {
                case 'io':
                    return CompletableFuture.supplyAsync(task, io)
                case 'common':
                    return CompletableFuture.supplyAsync(task)
                case 'common-wrapped':
                    return CompletableFuture.supplyAsync(PropagatedContext.wrapCurrent(task))
                default:
                    throw new IllegalArgumentException(executor)
            }
        }

        @Get('/reactor/{operator}/{scheduler}')
        @SingleResult
        Mono<Map<String, Object>> reactor(String operator, String scheduler) {
            service.reactive(operator, scheduler == 'parallel' ? parallel : boundedElastic)
        }

        @Get('/reactor-newspan/{operator}/{scheduler}')
        @SingleResult
        Mono<Map<String, Object>> reactorNewSpan(String operator, String scheduler) {
            service.reactiveNewSpan(operator, scheduler == 'parallel' ? parallel : boundedElastic)
        }

        @Get('/reactor-flatmap')
        @SingleResult
        Mono<Map<String, Object>> reactorFlatMap() {
            Flux.range(0, 4)
                .flatMap({ Integer i -> client.downstreamReactive().subscribeOn(parallel) }, 4)
                .collectList()
                .map { List<String> traces -> [downstream_traces: traces] as Map<String, Object> }
        }
    }
}
