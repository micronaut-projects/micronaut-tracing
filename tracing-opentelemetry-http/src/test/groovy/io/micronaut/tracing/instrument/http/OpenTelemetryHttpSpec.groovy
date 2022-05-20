package io.micronaut.tracing.instrument.http

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
import io.micronaut.reactor.http.client.ReactorHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import io.micronaut.tracing.opentelemetry.instrument.http.client.OpenTelemetryHttpClientConfig
import io.micronaut.tracing.opentelemetry.instrument.http.server.OpenTelemetryHttpServerConfig
import io.opentelemetry.extension.annotations.SpanAttribute
import io.opentelemetry.extension.annotations.WithSpan
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import spock.lang.AutoCleanup
import spock.lang.Specification

import static io.micronaut.scheduling.TaskExecutors.IO

class OpenTelemetryHttpSpec extends Specification {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryHttpSpec)

    String TRACING_ID = "X-TrackingId"
    String TRACING_ID_IN_SPAN = "http.request.header.x_trackingid"

    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'otel.http.client.request-headers': [TRACING_ID],
            'otel.http.client.response-headers': [TRACING_ID],
            'otel.http.server.request-headers': [TRACING_ID],
            'otel.http.server.response-headers': [TRACING_ID],
            'micronaut.application.name': 'test-app'
    ])

    @AutoCleanup
    ReactorHttpClient reactorHttpClient = ReactorHttpClient.create(embeddedServer.URL)

    void 'test map WithSpan annotation'() {
        def count = 5
        // 1x Server POST 2x Server GET 2x Client GET, 2x Method call with NewSpan  = 4
        def spanNumbers = 7
        def spanNumbersOfRequests = 5
        def context = embeddedServer.getApplicationContext()
        def testExporter = context.getBean(InMemorySpanExporter.class)

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
        for (Tuple2 t : result)
            assert t.getT1() == t.getT2()

        testExporter.getFinishedSpanItems().size() == count * spanNumbers

        testExporter.getFinishedSpanItems().attributes.any(x->x.asMap().keySet().any(y-> y.key == "tracing-annotation-span-attribute"))
        !testExporter.getFinishedSpanItems().attributes.any(x->x.asMap().keySet().any(y-> y.key == "tracing-annotation-span-tag-no-withspan"))
        testExporter.getFinishedSpanItems().attributes.any(x->x.asMap().keySet().any(y-> y.key == "tracing-annotation-span-tag-with-withspan"))
        // test if newspan has appended name
        testExporter.getFinishedSpanItems().name.any(x->x.contains("#test-withspan-mapping"))

        testExporter.getFinishedSpanItems().attributes.stream().filter(x->x.asMap().keySet().any(y-> y.key == TRACING_ID_IN_SPAN)).collect().size() == spanNumbersOfRequests * count

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
        private ReactorHttpClient reactorHttpClient

        @ExecuteOn(IO)
        @Post("/enter")
        @NewSpan("enter")
        Mono<String> enter(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            LOG.info("enter")
            return Mono.from(
                    reactorHttpClient.retrieve(HttpRequest
                            .GET("/annotations/test")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @ExecuteOn(IO)
        @Get("/test")
        @ContinueSpan
        Mono<String> test(@SpanAttribute("tracing-annotation-span-attribute") @Header("X-TrackingId") String tracingId) {
            LOG.info("test")
            return Mono.from(
                    reactorHttpClient.retrieve(HttpRequest
                            .GET("/annotations/test2")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @ExecuteOn(IO)
        @Get("/test2")
        Mono<String> test2(@SpanTag("tracing-annotation-span-tag-no-withspan") @Header("X-TrackingId") String tracingId) {
            LOG.info("test2")
            return methodWithSpan(tracingId)
        }

        @WithSpan("test-withspan-mapping")
        Mono<String> methodWithSpan(@SpanTag("tracing-annotation-span-tag-with-withspan") String tracingId){
            return Mono.just(tracingId)
        }

    }

}
