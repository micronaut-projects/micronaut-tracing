package io.micronaut.tracing.opentracing.instrument.http

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import static io.opentracing.propagation.Format.Builtin.HTTP_HEADERS

class OpenTracingServerFilterSpec extends Specification {

    void 'prefers extracted request context over active span'() {
        given:
        SpanContext extractedParent = Stub()
        SpanContext createdSpanContext = Stub()
        Scope scope = Mock()
        Span createdSpan = Mock() {
            2 * context() >> createdSpanContext
        }
        Tracer.SpanBuilder spanBuilder = Mock()
        Tracer tracer = Mock() {
            1 * extract(HTTP_HEADERS, _) >> extractedParent
            1 * buildSpan('GET /traced/hello') >> spanBuilder
            0 * activeSpan()
        }
        OpenTracingServerFilter filter = newFilter(tracer)
        HttpRequest<?> request = HttpRequest.GET('/traced/hello')

        when:
        Mono.from(filter.doFilter(request, okChain())).block()

        then:
        1 * spanBuilder.asChildOf(extractedParent) >> spanBuilder
        1 * spanBuilder.withTag('http.method', 'GET') >> spanBuilder
        1 * spanBuilder.withTag('http.path', '/traced/hello') >> spanBuilder
        1 * spanBuilder.start() >> createdSpan
        1 * createdSpan.setTag('http.server', true)
        1 * tracer.activateSpan(createdSpan) >> scope
        1 * tracer.inject(createdSpanContext, HTTP_HEADERS, _)
        1 * createdSpan.finish()
        1 * scope.close()
        request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span).orElseThrow().is(createdSpan)
    }

    void 'starts a root span when request has no tracing headers'() {
        given:
        SpanContext createdSpanContext = Stub()
        Scope scope = Mock()
        Span createdSpan = Mock() {
            2 * context() >> createdSpanContext
        }
        Tracer.SpanBuilder spanBuilder = Mock()
        Tracer tracer = Mock() {
            1 * extract(HTTP_HEADERS, _) >> null
            1 * buildSpan('GET /traced/hello') >> spanBuilder
            0 * activeSpan()
        }
        OpenTracingServerFilter filter = newFilter(tracer)
        HttpRequest<?> request = HttpRequest.GET('/traced/hello')

        when:
        Mono.from(filter.doFilter(request, okChain())).block()

        then:
        0 * spanBuilder.asChildOf(_)
        1 * spanBuilder.ignoreActiveSpan() >> spanBuilder
        1 * spanBuilder.withTag('http.method', 'GET') >> spanBuilder
        1 * spanBuilder.withTag('http.path', '/traced/hello') >> spanBuilder
        1 * spanBuilder.start() >> createdSpan
        1 * createdSpan.setTag('http.server', true)
        1 * tracer.activateSpan(createdSpan) >> scope
        1 * tracer.inject(createdSpanContext, HTTP_HEADERS, _)
        1 * createdSpan.finish()
        1 * scope.close()
        request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span).orElseThrow().is(createdSpan)
    }

    void 'finishes span and closes scope when chain errors'() {
        given:
        SpanContext extractedParent = Stub()
        SpanContext createdSpanContext = Stub()
        Scope scope = Mock()
        Throwable failure = new IllegalStateException('broken')
        Span createdSpan = Mock() {
            1 * context() >> createdSpanContext
        }
        Tracer.SpanBuilder spanBuilder = Mock()
        Tracer tracer = Mock() {
            1 * extract(HTTP_HEADERS, _) >> extractedParent
            1 * buildSpan('GET /traced/error') >> spanBuilder
            0 * activeSpan()
        }
        OpenTracingServerFilter filter = newFilter(tracer)
        HttpRequest<?> request = HttpRequest.GET('/traced/error')

        when:
        Mono.from(filter.doFilter(request, errorChain(failure))).block()

        then:
        def e = thrown(IllegalStateException)
        e.is(failure)
        1 * spanBuilder.asChildOf(extractedParent) >> spanBuilder
        1 * spanBuilder.withTag('http.method', 'GET') >> spanBuilder
        1 * spanBuilder.withTag('http.path', '/traced/error') >> spanBuilder
        1 * spanBuilder.start() >> createdSpan
        1 * createdSpan.setTag('http.server', true)
        1 * tracer.activateSpan(createdSpan) >> scope
        1 * createdSpan.setTag('error', 'broken')
        1 * createdSpan.finish()
        1 * scope.close()
        0 * tracer.inject(_, _, _)
        request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span).orElseThrow().is(createdSpan)
    }

    void 'sets a boolean error tag and logs the error when the boolean error tag is enabled'() {
        given:
        Throwable failure = new IllegalStateException('broken')
        Span createdSpan = Mock()
        Tracer.SpanBuilder spanBuilder = Stub()
        spanBuilder.withTag(_ as String, _ as String) >> spanBuilder
        spanBuilder.ignoreActiveSpan() >> spanBuilder
        spanBuilder.start() >> createdSpan
        Tracer tracer = Stub() {
            buildSpan(_) >> spanBuilder
        }
        OpenTracingServerFilter filter = newFilter(tracer)
        filter.setBooleanErrorTag(true)

        when:
        Mono.from(filter.doFilter(HttpRequest.GET('/traced/error'), errorChain(failure))).block()

        then:
        thrown(IllegalStateException)
        1 * createdSpan.setTag('error', true)
        0 * createdSpan.setTag('error', _ as String)
        1 * createdSpan.log([
            'event'       : 'error',
            'error.kind'  : IllegalStateException.name,
            'error.object': failure,
            'message'     : 'broken'
        ])
    }

    void 'sets a boolean error tag and logs the reason of an error response when the boolean error tag is enabled'() {
        given:
        Span createdSpan = Mock()
        Tracer.SpanBuilder spanBuilder = Stub()
        spanBuilder.withTag(_ as String, _ as String) >> spanBuilder
        spanBuilder.ignoreActiveSpan() >> spanBuilder
        spanBuilder.start() >> createdSpan
        Tracer tracer = Stub() {
            buildSpan(_) >> spanBuilder
        }
        OpenTracingServerFilter filter = newFilter(tracer)
        filter.setBooleanErrorTag(true)
        ServerFilterChain chain = new ServerFilterChain() {
            @Override
            Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request) {
                return Mono.just(HttpResponse.notFound())
            }
        }

        when:
        Mono.from(filter.doFilter(HttpRequest.GET('/traced/missing'), chain)).block()

        then:
        1 * createdSpan.setTag('http.status_code', 404)
        1 * createdSpan.setTag('error', true)
        1 * createdSpan.log(['event': 'error', 'message': 'Not Found'])
    }

    private static OpenTracingServerFilter newFilter(Tracer tracer) {
        new OpenTracingServerFilter(tracer, ConversionService.SHARED, null)
    }

    private static ServerFilterChain okChain() {
        return new ServerFilterChain() {
            @Override
            Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request) {
                return Mono.just(HttpResponse.ok())
            }
        }
    }

    private static ServerFilterChain errorChain(Throwable failure) {
        return new ServerFilterChain() {
            @Override
            Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request) {
                return Mono.error(failure)
            }
        }
    }
}
