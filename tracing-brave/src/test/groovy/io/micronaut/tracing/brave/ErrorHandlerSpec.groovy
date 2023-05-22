package io.micronaut.tracing.brave

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Issue
import spock.lang.Specification

import static io.micronaut.tracing.brave.sender.HttpClientSender.Builder.DEFAULT_SERVER_URL

class ErrorHandlerSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/3')
    void 'error handler only called once'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'spec.name': 'ErrorHandlerSpec',
                'tracing.zipkin.enabled': true,
                'tracing.instrument-threads': true,
                'tracing.zipkin.sampler.probability': 1,
                'tracing.zipkin.http.url': DEFAULT_SERVER_URL
        )
        def embeddedServer = context.getBean(EmbeddedServer).start()
        def client = context.createBean(HttpClient, embeddedServer.URL)
        def handler = context.getBean(ErrorHandler)

        when:
        client.toBlocking().exchange(HttpRequest.GET('/error-handler-controller'))

        then:
        thrown HttpClientResponseException
        handler.exceptions.size() == 1
        handler.exceptions[0].message == 'foo'

        cleanup:
        embeddedServer.close()
        client.close()
    }

    @Requires(property = 'spec.name', value = 'ErrorHandlerSpec')
    @Controller('/error-handler-controller')
    static class ErrorHandlerController {

        @Get
        String hello() {
            throw new RuntimeException('foo')
        }
    }
}
