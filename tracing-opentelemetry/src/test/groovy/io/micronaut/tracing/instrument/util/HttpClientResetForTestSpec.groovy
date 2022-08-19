package io.micronaut.tracing.instrument.util

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.reactor.http.client.ReactorHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.annotation.ContinueSpan
import io.opentelemetry.extension.annotations.SpanAttribute
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static io.micronaut.scheduling.TaskExecutors.IO

@Slf4j("LOG")
class HttpClientResetForTestSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.application.name': 'test-app'
    ])

    @Shared
    @AutoCleanup
    ReactorHttpClient reactorHttpClient = ReactorHttpClient.create(embeddedServer.URL)

    private PollingConditions conditions = new PollingConditions()

    void 'test map WithSpan annotation'() {
        int count = 1
        // 2x Method call 1x NewSpan, 1x WithSpan  = 2
        int spanNumbers = 2
        def testExporter = embeddedServer.applicationContext.getBean(InMemorySpanExporter)
        String tracingId = UUID.randomUUID()

        expect:
        HttpRequest<Object> request = HttpRequest
                .GET("/test/test")
                .header("X-TrackingId", tracingId)
        def resp = reactorHttpClient.toBlocking().retrieve(request)
        resp == tracingId

        cleanup:
        testExporter.reset()
    }

    @Controller("/test")
    static class TestController {


        @ExecuteOn(IO)
        @Get("/test")
        @ContinueSpan
        Mono<String> test(@SpanAttribute("tracing-annotation-span-attribute")
                          @Header("X-TrackingId") String tracingId) {
            LOG.debug("test")
            return Mono.just(tracingId)
        }
    }
}
