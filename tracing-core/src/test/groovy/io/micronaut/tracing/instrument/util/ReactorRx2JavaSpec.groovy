package io.micronaut.tracing.instrument.util

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.reactor.http.client.ReactorHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.rxjava2.http.client.RxHttpClient
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.reactivex.Flowable
import io.reactivex.Single
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.scheduling.TaskExecutors.IO

@Slf4j("LOG")
class ReactorRx2JavaSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    RxHttpClient rxHttpClient = RxHttpClient.create(embeddedServer.URL)

    void "test RxJava2 integration"() {
        expect:
        List<Tuple2> result = Flux.range(1, 1)
                .flatMap {
                    String tracingId = UUID.randomUUID()
                    HttpRequest<Object> request = HttpRequest
                            .POST("/rxjava2/enter", new SomeBody())
                            .header("X-TrackingId", tracingId)
                    return Mono.from(rxHttpClient.retrieve(request)).map(response -> {
                        Tuples.of(tracingId, response)
                    })
                }
                .collectList()
                .block()
        for (Tuple2 t : result)
            assert t.getT1() == t.getT2()
    }

    @Introspected
    static class SomeBody {
    }

    @Controller("/rxjava2")
    static class RxJava2Controller {

        @Inject
        @Client("/")
        private RxHttpClient rxHttpClient

        @Inject
        @Client("/")
        private ReactorHttpClient reactorHttpClient

        @ExecuteOn(IO)
        @ContinueSpan
        @Post("/enter")
        Single<String> test(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            LOG.debug("enter")
            return Single.fromPublisher(
                    reactorHttpClient.retrieve(HttpRequest
                            .GET("/rxjava2/test")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @ExecuteOn(IO)
        @Get("/test")
        @ContinueSpan
        Mono<String> testRxJava2(@Header("X-TrackingId") String tracingId) {
            LOG.debug("test")
            return Mono.from(
                    rxHttpClient.exchange(HttpRequest
                            .GET("/rxjava2/test2")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @ExecuteOn(IO)
        @Get("/test2")
        @ContinueSpan
        String test2RxJava2(@Header("X-TrackingId") String tracingId) {
            LOG.debug("test2")
            return Flux.from(trackingIdRxJava2(tracingId)).blockFirst()
        }


        @NewSpan("test")
        Flowable<String> trackingIdRxJava2(String tracingId) {
           return Flowable.just(tracingId, tracingId, tracingId)
        }

    }

}
