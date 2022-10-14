package io.micronaut.tracing.instrument.util

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.annotation.ContinueSpan
import io.opentelemetry.api.logs.GlobalLoggerProvider
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.scheduling.TaskExecutors.IO

@Slf4j("LOG")
class HttpClientResetForTestSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.application.name': 'test-app',
            'spec.name': 'HttpClientResetForTestSpec'
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = HttpClient.create(embeddedServer.URL)

    def cleanup() {
        GlobalLoggerProvider.resetForTest()
    }

    void 'test pre destroy resetForTest'() {
        given:
        String tracingId = UUID.randomUUID()

        when:
        HttpRequest<Object> request = HttpRequest
                .GET("/test/test")
                .accept(MediaType.TEXT_PLAIN)
                .header("X-TrackingId", tracingId)
        String resp = httpClient.toBlocking().retrieve(request)

        then:
        noExceptionThrown()
        resp == tracingId
    }

    @Requires(property = "spec.name", value = "HttpClientResetForTestSpec")
    @Controller("/test")
    static class TestController {

        @ExecuteOn(IO)
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/test")
        @ContinueSpan
        Mono<String> test(@SpanAttribute("tracing-annotation-span-attribute")
                          @Header("X-TrackingId") String tracingId) {
            LOG.debug("test")
            return Mono.just(tracingId)
        }
    }
}
