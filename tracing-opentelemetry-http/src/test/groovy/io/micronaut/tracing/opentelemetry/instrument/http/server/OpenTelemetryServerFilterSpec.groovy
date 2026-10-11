package io.micronaut.tracing.opentelemetry.instrument.http.server

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration

class OpenTelemetryServerFilterSpec extends Specification {

    private static final ContextKey<String> SPAN = ContextKey.named('span')

    Instrumenter<HttpRequest<?>, Object> instrumenter = Mock()
    ServerFilterChain chain = Mock()
    OpenTelemetryServerFilter filter = new OpenTelemetryServerFilter(null, instrumenter)

    void 'ends the server span when the request is cancelled before a response'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        Context spanContext = Context.root().with(SPAN, 'first')

        when:
        Mono.from(filter.doFilter(request, chain)).subscribe().dispose()

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        1 * chain.proceed(request) >> Mono.never()
        1 * instrumenter.end(spanContext, request, null, null)
        request.getAttribute(OpenTelemetryServerResponseFilter.FINISHED, Boolean).orElse(false)

        when: 'the response filters run anyway'
        new OpenTelemetryServerResponseFilter(instrumenter).finishResponse(request, HttpResponse.ok())

        then: 'the span is not ended twice'
        0 * instrumenter.end(_, _, _, _)
    }

    void 'does not end the server span on cancel when it is already finished'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        Context spanContext = Context.root().with(SPAN, 'first')

        when:
        Mono.from(filter.doFilter(request, chain)).subscribe().dispose()

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        1 * chain.proceed(request) >> {
            request.setAttribute(OpenTelemetryServerResponseFilter.FINISHED, true)
            Mono.never()
        }
        0 * instrumenter.end(_, _, _, _)
    }

    void 'does not end the server span on cancel after the response was emitted'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        MutableHttpResponse<Object> response = HttpResponse.ok()
        Context spanContext = Context.root().with(SPAN, 'first')

        when: 'downstream cancels after receiving the response, before the response filters ran'
        Flux.from(filter.doFilter(request, chain)).take(1).blockLast(Duration.ofSeconds(3))

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        1 * chain.proceed(request) >> Mono.just(response)
        0 * instrumenter.end(_, _, _, _)

        when:
        new OpenTelemetryServerResponseFilter(instrumenter).finishResponse(request, response)

        then:
        1 * instrumenter.end(spanContext, request, response, null)
    }

    void 'a span started when the filter runs again for the same request is finished'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        MutableHttpResponse<Object> response = HttpResponse.ok()
        Context first = Context.root().with(SPAN, 'first')
        Context second = Context.root().with(SPAN, 'second')
        def error = new IllegalStateException('boom')

        when: 'the first pass fails'
        Mono.from(filter.doFilter(request, chain)).onErrorResume { Mono.empty() }.block()

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> first
        1 * chain.proceed(request) >> Mono.error(error)
        1 * instrumenter.end(first, request, null, error)
        request.getAttribute(OpenTelemetryServerResponseFilter.FINISHED, Boolean).orElse(false)

        when: 'the filter runs again for the same request'
        Mono.from(filter.doFilter(request, chain)).block()

        then: 'a new span is started and it is not marked as finished'
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> second
        1 * chain.proceed(request) >> Mono.just(response)
        !request.getAttribute(OpenTelemetryServerResponseFilter.FINISHED, Boolean).orElse(false)

        when:
        new OpenTelemetryServerResponseFilter(instrumenter).finishResponse(request, response)

        then: 'the response filter ends the new span'
        1 * instrumenter.end(second, request, response, null)
    }
}
