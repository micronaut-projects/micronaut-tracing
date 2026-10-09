package io.micronaut.tracing.opentelemetry.instrument.http.client

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.FilterContinuation
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

class OpenTelemetryClientFilterSpec extends Specification {

    private static final ContextKey<String> SPAN = ContextKey.named('span')

    Instrumenter<MutableHttpRequest<?>, Object> instrumenter = Mock()
    OpenTelemetryClientFilter filter = new OpenTelemetryClientFilter(null, instrumenter)

    void 'ends the client span when the request is cancelled'() {
        given:
        MutableHttpRequest<Object> request = HttpRequest.GET('/test')
        Context spanContext = Context.root().with(SPAN, 'client')
        def continuation = new TestContinuation()

        when:
        def result = filter.doFilter(request, continuation)
        result.toCompletableFuture().cancel(false)

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        1 * instrumenter.end(spanContext, request, null, null)
        continuation.downstream.isCancelled()
    }

    void 'ends the client span only once when cancelled after the response'() {
        given:
        MutableHttpRequest<Object> request = HttpRequest.GET('/test')
        HttpResponse<Object> response = HttpResponse.ok()
        Context spanContext = Context.root().with(SPAN, 'client')
        def continuation = new TestContinuation()

        when:
        def result = filter.doFilter(request, continuation)
        continuation.downstream.complete(response)
        result.toCompletableFuture().cancel(false)

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        1 * instrumenter.end(spanContext, request, response, null)
        result.toCompletableFuture().get(3, TimeUnit.SECONDS) == response
    }

    void 'ends the client span with the response of a failure'() {
        given:
        MutableHttpRequest<Object> request = HttpRequest.GET('/test')
        HttpResponse<Object> response = HttpResponse.status(HttpStatus.NOT_FOUND)
        def error = new HttpClientResponseException('Not Found', response)
        Context spanContext = Context.root().with(SPAN, 'client')
        def continuation = new TestContinuation()

        when:
        def result = filter.doFilter(request, continuation)
        continuation.downstream.completeExceptionally(error)

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        1 * instrumenter.end(spanContext, request, response, error)
        result.toCompletableFuture().isCompletedExceptionally()
    }

    void 'the client span is current for the downstream only'() {
        given:
        MutableHttpRequest<Object> request = HttpRequest.GET('/test')
        HttpResponse<Object> response = HttpResponse.ok()
        Context spanContext = Context.root().with(SPAN, 'client')
        Context currentInDownstream = null
        Context currentOnCompletion = null
        def downstream = new CompletableFuture<HttpResponse<?>>()
        def continuation = new TestContinuation() {
            @Override
            CompletionStage<HttpResponse<?>> proceed() {
                currentInDownstream = Context.current()
                return downstream
            }
        }

        when:
        def result = filter.doFilter(request, continuation).thenApply {
            currentOnCompletion = Context.current()
            it
        }
        // the downstream completes in its own context, with the client span current
        try (def ignored = spanContext.makeCurrent()) {
            downstream.complete(response)
        }

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        1 * instrumenter.end(spanContext, request, response, null)
        result.toCompletableFuture().get(3, TimeUnit.SECONDS) == response
        currentInDownstream.get(SPAN) == 'client'
        currentOnCompletion.get(SPAN) == null
    }

    void 'falls back to the original request when the instrumenter returns no context'() {
        given:
        MutableHttpRequest<Object> request = HttpRequest.GET('/test')
        HttpResponse<Object> response = HttpResponse.ok()
        def continuation = new TestContinuation()

        when:
        def result = filter.doFilter(request, continuation)
        continuation.downstream.complete(response)

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> null
        0 * instrumenter.end(_, _, _, _)
        result.is(continuation.downstream)
        result.toCompletableFuture().get(3, TimeUnit.SECONDS) == response
    }

    static class TestContinuation implements FilterContinuation<CompletionStage<HttpResponse<?>>> {

        final CompletableFuture<HttpResponse<?>> downstream = new CompletableFuture<>()

        @Override
        FilterContinuation<CompletionStage<HttpResponse<?>>> request(HttpRequest<?> request) {
            return this
        }

        @Override
        CompletionStage<HttpResponse<?>> proceed() {
            return downstream
        }
    }
}
