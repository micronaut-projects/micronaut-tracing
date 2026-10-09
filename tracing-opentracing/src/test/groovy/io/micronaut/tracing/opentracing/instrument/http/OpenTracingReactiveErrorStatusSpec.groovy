package io.micronaut.tracing.opentracing.instrument.http

import io.jaegertracing.internal.metrics.InMemoryMetricsFactory
import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.reactivex.rxjava3.core.Flowable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/170')
class OpenTracingReactiveErrorStatusSpec extends Specification {

    static final String SPEC_NAME = 'OpenTracingReactiveErrorStatusSpec'

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.builder('spec.name': SPEC_NAME, 'tracing.jaeger.enabled': true, 'tracing.jaeger.sampler.probability': 1).singletons(new InMemoryReporter(), new InMemoryMetricsFactory()).run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    @Unroll
    void 'issue 170 - HttpStatusException from #path is returned as 400, not 500'() {
        when:
        client.toBlocking().exchange("/issue170/$path", String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
        e.response.getBody(String).orElse('').contains('wrong isolation')

        where:
        path << ['continue-flowable-error', 'continue-flowable-throw', 'continue-flux-error', 'continue-flux-throw',
                 'continue-mono-throw', 'new-flowable-throw', 'new-flux-throw', 'new-mono-throw']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/issue170')
    static class Issue170Controller {

        @Get('/continue-flowable-error')
        @ContinueSpan
        Flowable<String> continueFlowableError() {
            try {
                throwException()
            } catch (Exception e) {
                return Flowable.error(e)
            }
            Flowable.just('example value')
        }

        @Get('/continue-flowable-throw')
        @ContinueSpan
        Flowable<String> continueFlowableThrow() {
            throwException()
            Flowable.just('example value')
        }

        @Get('/continue-flux-error')
        @ContinueSpan
        Flux<String> continueFluxError() {
            try {
                throwException()
            } catch (Exception e) {
                return Flux.error(e)
            }
            Flux.just('example value')
        }

        @Get('/continue-flux-throw')
        @ContinueSpan
        Flux<String> continueFluxThrow() {
            throwException()
            Flux.just('example value')
        }

        @Get('/continue-mono-throw')
        @ContinueSpan
        Mono<String> continueMonoThrow() {
            throwException()
            Mono.just('example value')
        }

        @Get('/new-flowable-throw')
        @NewSpan
        Flowable<String> newFlowableThrow() {
            throwException()
            Flowable.just('example value')
        }

        @Get('/new-flux-throw')
        @NewSpan
        Flux<String> newFluxThrow() {
            throwException()
            Flux.just('example value')
        }

        @Get('/new-mono-throw')
        @NewSpan
        Mono<String> newMonoThrow() {
            throwException()
            Mono.just('example value')
        }

        static void throwException() {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, 'wrong isolation')
        }
    }
}
