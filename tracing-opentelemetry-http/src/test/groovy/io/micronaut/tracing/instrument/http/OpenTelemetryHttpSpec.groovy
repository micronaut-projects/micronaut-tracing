package io.micronaut.tracing.instrument.http

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.reactor.http.client.ReactorHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.rxjava2.http.client.RxHttpClient
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.reactivex.Single
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static io.micronaut.scheduling.TaskExecutors.IO

@Slf4j("LOG")
class OpenTelemetryHttpSpec extends Specification {

    String TRACING_ID = "X-TrackingId"
    String TRACING_ID_IN_SPAN = "http.request.header.x_trackingid"

    @AutoCleanup
    private ApplicationContext context

    @AutoCleanup
    ReactorHttpClient reactorHttpClient

    private PollingConditions conditions = new PollingConditions()

    @AutoCleanup
    private EmbeddedServer embeddedServer

    @AutoCleanup
    private EmbeddedServer dummy = ApplicationContext.builder().start().getBean(EmbeddedServer).start()

    private InMemorySpanExporter exporter

    void setup() {
        context = ApplicationContext.builder(
            'micronaut.http.services.correctspanname.url': "http://localhost:${dummy.port}",
            'otel.http.client.request-headers': [TRACING_ID],
            'otel.http.client.response-headers': [TRACING_ID],
            'otel.http.server.request-headers': [TRACING_ID],
            'otel.http.server.response-headers': [TRACING_ID],
            'otel.register.global':false,
            'micronaut.application.name': 'test-app',
            'otel.exclusions[0]': '.*exclude.*'
        ).start()

        embeddedServer = context.getBean(EmbeddedServer).start()
        reactorHttpClient = ReactorHttpClient.create(embeddedServer.URL)
        exporter = context.getBean(InMemorySpanExporter)
    }

    void 'test map WithSpan annotation'() {
        int count = 1
        // 1x Server POST 2x Server GET 2x Client GET, 3x Method call with NewSpan
        int clientSpanCount = 2
        int serverSpanCount = 3
        int internalSpanCount = 3

        int spanNumbers = clientSpanCount + serverSpanCount + internalSpanCount
        def spanNumbersOfRequests = 5

        expect:
        List<Tuple2> result = Flux.range(1, count)
                .flatMap {
                    String tracingId = UUID.randomUUID()
                    HttpRequest<Object> request = HttpRequest
                            .POST("/annotations/enter", new SomeBody())
                            .header(TRACING_ID, tracingId)
                    return Mono.from(reactorHttpClient.retrieve(request)).map(response -> {
                        Tuples.of(tracingId, response)
                    })
                }
                .collectList()
                .block()
        for (Tuple2 t : result) {
            assert t.getT1() == t.getT2()
        }
        conditions.eventually {
            exporter.finishedSpanItems.size() == count * spanNumbers
            exporter.finishedSpanItems.kind.stream().filter(x -> x == SpanKind.INTERNAL).collect().size() == internalSpanCount
            exporter.finishedSpanItems.kind.stream().filter(x -> x == SpanKind.SERVER).collect().size() == serverSpanCount
            exporter.finishedSpanItems.kind.stream().filter(x -> x == SpanKind.CLIENT).collect().size() == clientSpanCount

            exporter.finishedSpanItems.attributes.any(x -> x.asMap().keySet().any(y -> y.key == "tracing-annotation-span-attribute"))
            !exporter.finishedSpanItems.attributes.any(x -> x.asMap().keySet().any(y -> y.key == "tracing-annotation-span-tag-no-withspan"))
            exporter.finishedSpanItems.attributes.any(x -> x.asMap().keySet().any(y -> y.key == "tracing-annotation-span-tag-with-withspan"))
            exporter.finishedSpanItems.attributes.any(x -> x.asMap().keySet().any(y -> y.key == "privateMethodTestAttribute"))

            // test if newspan has appended name
            exporter.finishedSpanItems.name.any(x -> x.contains("#test-withspan-mapping"))

            exporter.getFinishedSpanItems().attributes.stream().filter(x -> x.asMap().keySet().any(y -> y.key == TRACING_ID_IN_SPAN)).collect().size() == spanNumbersOfRequests * count
        }
        cleanup:
        exporter.reset()
    }

    void 'test context propagation'() {
        when:
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange('/propagate/context', String)

        then:
        conditions.eventually {
            response
            response.body() == "contains micronaut.http.server.request: true, size: 1"
        }
        cleanup:
        exporter.reset()
    }

    void 'test openTelemetry rxjava2'() {
        def serverSpanCount = 2
        def clientSpanCount = 1
        def internalSpanCount = 1

        when:
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange('/rxjava2/test', String)

        then:
        conditions.eventually {
            response
            exporter.finishedSpanItems.size() == internalSpanCount + serverSpanCount + clientSpanCount
        }

        cleanup:
        exporter.reset()
    }

    void 'test exclude endpoint'() {
        when:
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange('/exclude/test', String)

        then:
        conditions.eventually {
            response
            exporter.getFinishedSpanItems().size() == 0
        }

        cleanup:
        exporter.reset()
    }

    void 'test error #desc, path=#path'() {
        when:
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange(path, String)

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error"
        conditions.eventually {
            exporter.finishedSpanItems.size() == spanCount
            exporter.finishedSpanItems.events.any { it.size() > 0 && it.get(0).name == "exception" }
            exporter.finishedSpanItems.stream().allMatch(span -> span.status.statusCode == StatusCode.ERROR)
        }
        cleanup:
        exporter.reset()
        where:
        path                                      | spanCount | desc
        '/error/publisher'                        | 2         | 'inside publisher'
        '/error/publisherErrorContinueSpan'       | 1         | 'inside continueSpan publisher'
        '/error/mono'                             | 2         | 'propagated through publisher'
        '/error/sync'                             | 2         | 'inside normal function'
        '/error/completionStage'                  | 2         | 'inside completionStage'
        '/error/completionStagePropagation'       | 2         | 'propagated through  completionStage'
        '/error/completionStageErrorContinueSpan' | 1         | 'inside normal method continueSpan'
    }

    void 'client with tracing annotations'() {
        def testExporter = embeddedServer.applicationContext.getBean(InMemorySpanExporter)
        def warehouseClient = embeddedServer.applicationContext.getBean(WarehouseClient)
        def serverSpanCount = 2
        def clientSpanCount = 2
        def internalSpanCount = 1

        expect:

        warehouseClient.order(Collections.singletonMap("testOrderKey", "testOrderValue"))
        warehouseClient.getItemCount("testItemCount", 10) == 10
        conditions.eventually {
            testExporter.finishedSpanItems.size() == serverSpanCount + clientSpanCount + internalSpanCount
            testExporter.finishedSpanItems.name.contains("WarehouseClient.order")
            testExporter.finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("warehouse.order")) == "{testOrderKey=testOrderValue}")
            testExporter.finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("upc")) == "10")
            testExporter.finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("net.peer.name")) == "localhost")
        }

        cleanup:
        testExporter.reset()
    }

    void 'client with tracing annotations that contains id inside annotation'() {
        def testExporter = embeddedServer.applicationContext.getBean(InMemorySpanExporter)
        def warehouseClient = embeddedServer.applicationContext.getBean(WarehouseClientWithId)
        def serverSpanCount = 1
        def clientSpanCount = 1

        expect:

        warehouseClient.order(Collections.singletonMap("testOrderKey", "testOrderValue"))
        conditions.eventually {
            testExporter.finishedSpanItems.size() == serverSpanCount + clientSpanCount
            testExporter.finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("warehouse.order")) == "{testOrderKey=testOrderValue}")
            testExporter.finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("net.peer.name")) == "correctspanname")
        }

        cleanup:
        testExporter.reset()
    }

    void 'test error 404'() {
        when:
        def route = '/error/notFoundRoute'
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange(route, String)

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Not Found"
        conditions.eventually {
            exporter.finishedSpanItems.size() == 1
            exporter.finishedSpanItems[0].name == "GET - " + route
            exporter.finishedSpanItems[0].status.statusCode == StatusCode.ERROR
        }
        cleanup:
        exporter.reset()
    }

    void 'route match template is added as route attribute'() {
        def testExporter = embeddedServer.applicationContext.getBean(InMemorySpanExporter)
        def warehouseClient = embeddedServer.applicationContext.getBean(WarehouseClient)

        expect:

        warehouseClient.order(UUID.randomUUID())
        conditions.eventually {
            testExporter.finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("http.route")) == "/client/order/{orderId}")
        }

        cleanup:
        testExporter.reset()
    }

    void 'query variables are not included in route template attribute'() {
        def testExporter = embeddedServer.applicationContext.getBean(InMemorySpanExporter)
        def warehouseClient = embeddedServer.applicationContext.getBean(WarehouseClient)

        expect:

        warehouseClient.order(UUID.randomUUID(), UUID.randomUUID())
        conditions.eventually {
            testExporter.finishedSpanItems.attributes.stream().anyMatch(x -> x.get(AttributeKey.stringKey("http.route")) == "/client/order/{orderId}")
        }

        cleanup:
        testExporter.reset()
    }

    @Introspected
    static class SomeBody {
    }

    @Controller("/annotations")
    static class TestController {

        @Inject
        @Client("/")
        ReactorHttpClient reactorHttpClient

        @ExecuteOn(IO)
        @Post("/enter")
        @NewSpan("enter")
        Mono<String> enter(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            LOG.debug("enter")
            return Mono.from(
                    reactorHttpClient.retrieve(HttpRequest
                            .GET("/annotations/test")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @ExecuteOn(IO)
        @Get("/test")
        @ContinueSpan
        Mono<String> test(@SpanAttribute("tracing-annotation-span-attribute")
                          @Header("X-TrackingId") String tracingId) {
            LOG.debug("test")
            privateMethodTest(tracingId)
            return Mono.from(
                    reactorHttpClient.retrieve(HttpRequest
                            .GET("/annotations/test2")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @ContinueSpan
        void privateMethodTest(@SpanAttribute("privateMethodTestAttribute") String traceId) {

        }

        @ExecuteOn(IO)
        @Get("/test2")
        Mono<String> test2(@SpanTag("tracing-annotation-span-tag-no-withspan")
                           @Header("X-TrackingId") String tracingId) {
            LOG.debug("test2")
            methodWithSpan(tracingId).toCompletableFuture().get()
            return Mono.just(tracingId)
        }

        @WithSpan("test-withspan-mapping")
        CompletionStage<Void> methodWithSpan(@SpanTag("tracing-annotation-span-tag-with-withspan") String tracingId) {
            return CompletableFuture.runAsync(() -> {return normalFunctionWithNewSpan(tracingId)})
        }

        @NewSpan
        String normalFunctionWithNewSpan(String tracingId) {
            return tracingId
        }
    }

    @Controller('/propagate')
    static class ContextPropagateController {

        @Get("/context")
        Mono<String> context() {

            return Mono.deferContextual(ctx -> {
                boolean hasKey = ctx.hasKey(ServerRequestContext.KEY)
                int size = ctx.size()
                return Mono.just("contains ${ServerRequestContext.KEY}: $hasKey, size: $size")
            }) as Mono<String>
        }
    }

    @Controller('/error')
    static class ErrorController {

        @Get("/publisher")
        @WithSpan
        Mono<Void> publisher() {
            throw new RuntimeException("publisher")
        }

        @Get("/publisherErrorContinueSpan")
        Mono<Void> publisherErrorContinueSpan() {
            return Mono.from(continueSpanPublisher())
        }

        @ContinueSpan
        Mono<Void> continueSpanPublisher() {
            throw new RuntimeException("publisherErrorContinueSpan")
        }

        @Get("/mono")
        @WithSpan
        Mono<Void> mono() {
            return Mono.error(new RuntimeException("publisher"))
        }

        @Get("/sync")
        @WithSpan
        void sync() {
            throw new RuntimeException("sync")
        }

        @Get("/completionStage")
        @WithSpan
        CompletionStage<Void> completionStage (){
            throw new RuntimeException("completionStage")
        }

        @Get("/completionStagePropagation")
        @WithSpan
        CompletionStage<Void> completionStagePropagation (){
            return CompletableFuture.runAsync( ()-> { throw new RuntimeException("completionStage")})
        }

        @Get("/completionStageErrorContinueSpan")
        CompletionStage<Void> completionStageErrorContinueSpan () {
            throwAnError()
            return null
        }

        @ContinueSpan
        void throwAnError() {
            throw new RuntimeException("throwAnError")
        }
    }

    @Controller('/exclude')
    static class ExcludeController {

        @Get("/test")
        void excludeTest() {}
    }

    @Controller('/rxjava2')
    static class RxJava2 {

        @Inject
        @Client("/")
        RxHttpClient rxHttpClient

        @Get("/test")
        Single<String> test() {
            return Single.fromPublisher(
                    rxHttpClient.retrieve(HttpRequest
                            .GET("/rxjava2/test2"), String)
            )
        }

        @NewSpan
        @Get("/test2")
        Single<String> test2() {
            dummyMethodThatWillNotProduceSpan()
            return Single.just("test2")
        }

        void dummyMethodThatWillNotProduceSpan() {}

    }

    @Controller("/client")
    static class ClientController {

        @Get("/count")
        int getItemCount(@QueryValue String store, @SpanTag @QueryValue int upc) {
            return upc
        }


        @Post("/order")
        void order(@SpanTag("warehouse.order") Map<String, ?> json) {

        }

        @Get("/order/{orderId}")
        void order(@PathVariable("orderId") UUID orderId, @Nullable @QueryValue("customerId") UUID customerId) {

        }


    }

    @Client("/client")
    static interface WarehouseClient {

        @Get("/count")
        @ContinueSpan
        int getItemCount(@QueryValue String store, @SpanTag @QueryValue int upc);

        @Post("/order")
        @NewSpan
        void order(@SpanTag("warehouse.order") Map<String, ?> json);

        @Get("/order/{orderId}")
        void order(UUID orderId);

        @Get("/order/{orderId}?customerId={customerId}")
        void order(UUID orderId, UUID customerId);

    }

    @Client(id = "correctspanname")
    static interface WarehouseClientWithId {

        @Post("/order")
        @NewSpan
        void order(@SpanTag("warehouse.order") Map<String, ?> json);

    }

}
