package io.micronaut.tracing.opentelemetry.instrument.http.server

import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.FilterContinuation
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.micronaut.web.router.RouteAttributes
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration

class OpenTelemetryServerFilterSpec extends Specification {

    private static final ContextKey<String> SPAN = ContextKey.named('span')

    Instrumenter<HttpRequest<?>, Object> instrumenter = Mock()
    OpenTelemetryServerFilter filter = new OpenTelemetryServerFilter(null, instrumenter)

    void 'ends the server span when the request is cancelled before a response'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        Context spanContext = Context.root().with(SPAN, 'first')

        when:
        Mono.from(filter.startSpan(request, emptyContext(), new TestContinuation(Mono.never()))).subscribe().dispose()

        then:
        1 * instrumenter.shouldStart(Context.root(), request) >> true
        1 * instrumenter.start(Context.root(), request) >> spanContext
        1 * instrumenter.end(spanContext, request, null, null)

        when: 'the response filters run anyway'
        filter.endSpan(request, HttpResponse.ok())

        then: 'the span is not ended twice'
        0 * instrumenter.end(_, _, _, _)
    }

    void 'does not end the server span on cancel after the response was emitted'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        MutableHttpResponse<Object> response = HttpResponse.ok()
        Context spanContext = Context.root().with(SPAN, 'first')

        when: 'downstream cancels after receiving the response, before the response filters ran'
        Flux.from(filter.startSpan(request, emptyContext(), new TestContinuation(Flux.just(response, response))))
            .take(1).blockLast(Duration.ofSeconds(3))

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        0 * instrumenter.end(_, _, _, _)

        when:
        filter.endSpan(request, response)

        then:
        1 * instrumenter.end(spanContext, request, response, null)
    }

    void 'the span is propagated to the downstream and ended once by the response filter'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        MutableHttpResponse<Object> response = HttpResponse.ok()
        Context spanContext = Context.root().with(SPAN, 'first')
        def propagatedContext = emptyContext()

        when:
        Mono.from(filter.startSpan(request, propagatedContext, new TestContinuation(Mono.just(response)))).block()

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        0 * instrumenter.end(_, _, _, _)
        propagatedContext.context.findOrNull(OpenTelemetryPropagationContext).context() == spanContext

        when:
        filter.endSpan(request, response)
        filter.endSpan(request, response)

        then:
        1 * instrumenter.end(spanContext, request, response, null)
    }

    void 'the parent is the propagated context, not the current thread context'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        Context parent = Context.root().with(SPAN, 'parent')
        def propagatedContext = MutablePropagatedContext.of(PropagatedContext.empty().plus(new OpenTelemetryPropagationContext(parent)))

        when:
        def scope = Context.root().with(SPAN, 'leaked').makeCurrent()
        try {
            filter.startSpan(request, propagatedContext, new TestContinuation(Mono.never()))
        } finally {
            scope.close()
        }

        then:
        1 * instrumenter.shouldStart(parent, request) >> false
        0 * instrumenter.start(_, _)
    }

    void 'an exception of the route or an error status marks the span as an error'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        Context spanContext = Context.root().with(SPAN, 'first')
        def exception = new IllegalStateException('boom')
        MutableHttpResponse<Object> response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
        RouteAttributes.setException(response, exception)

        when:
        filter.startSpan(request, emptyContext(), new TestContinuation(Mono.never()))
        filter.endSpan(request, response)

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> spanContext
        1 * instrumenter.end(spanContext, request, response, exception)
    }

    void 'a span started when the filter runs again for the same request is finished'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        MutableHttpResponse<Object> response = HttpResponse.ok()
        Context first = Context.root().with(SPAN, 'first')
        Context second = Context.root().with(SPAN, 'second')
        def error = new IllegalStateException('boom')

        when: 'the first pass fails'
        Mono.from(filter.startSpan(request, emptyContext(), new TestContinuation(Mono.error(error))))
            .onErrorResume { Mono.empty() }.block()

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> first
        1 * instrumenter.end(first, request, null, error)

        when: 'the filter runs again for the same request'
        Mono.from(filter.startSpan(request, emptyContext(), new TestContinuation(Mono.just(response)))).block()
        filter.endSpan(request, response)

        then: 'a new span is started and ended'
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> second
        1 * instrumenter.end(second, request, response, null)
    }

    void 'no second span is started while the span of the request is in flight'() {
        given:
        HttpRequest<Object> request = HttpRequest.GET('/test')
        Context first = Context.root().with(SPAN, 'first')
        Publisher<HttpResponse<?>> second = Mono.never()

        when:
        filter.startSpan(request, emptyContext(), new TestContinuation(Mono.never()))
        def secondResult = filter.startSpan(request, emptyContext(), new TestContinuation(second))

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> first
        secondResult.is(second)
    }

    private static MutablePropagatedContext emptyContext() {
        MutablePropagatedContext.of(PropagatedContext.empty())
    }

    static class TestContinuation implements FilterContinuation<Publisher<HttpResponse<?>>> {

        final Publisher<HttpResponse<?>> downstream

        TestContinuation(Publisher<HttpResponse<?>> downstream) {
            this.downstream = downstream
        }

        @Override
        FilterContinuation<Publisher<HttpResponse<?>>> request(HttpRequest<?> request) {
            return this
        }

        @Override
        Publisher<HttpResponse<?>> proceed() {
            return downstream
        }
    }
}
