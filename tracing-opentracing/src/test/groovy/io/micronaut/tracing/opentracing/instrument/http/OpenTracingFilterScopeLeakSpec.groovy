package io.micronaut.tracing.opentracing.instrument.http

import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.tracing.opentracing.OpenTracingPropagationContext
import io.opentracing.Span
import io.opentracing.Tracer
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicReference

/**
 * Verifies that the OpenTracing HTTP filters never leave the request span or its
 * {@link PropagatedContext} installed on the subscribing thread once {@code subscribe()} returns.
 */
class OpenTracingFilterScopeLeakSpec extends Specification {

    @AutoCleanup
    ApplicationContext context

    InMemoryReporter reporter

    Tracer tracer

    void setup() {
        reporter = new InMemoryReporter()
        context = ApplicationContext.builder([
                'tracing.jaeger.enabled'            : true,
                'tracing.jaeger.sampler.probability': 1
        ]).singletons(reporter).start()
        tracer = context.getBean(Tracer)
    }

    void 'server filter does not leak request span to subscribing thread with never-completing downstream'() {
        given:
        def filter = new OpenTracingServerFilter(tracer, ConversionService.SHARED, null)
        def request = HttpRequest.GET('/leak-server')
        AtomicReference<Span> spanDuringSubscribe = new AtomicReference<>()
        AtomicReference<Span> spanFromReactorContext = new AtomicReference<>()
        def publisher = filter.doFilter(request, { req ->
            downstream(spanDuringSubscribe, spanFromReactorContext, Mono.never())
        } as ServerFilterChain)

        when:
        def subscription = Mono.from(publisher).subscribe()
        Span requestSpan = request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span).orElseThrow()

        then: 'the downstream subscription ran with the request span active'
        spanDuringSubscribe.get() == requestSpan
        spanFromReactorContext.get() == requestSpan

        and: 'nothing is left installed on the subscribing thread'
        tracer.activeSpan() == null
        !PropagatedContext.exists()

        cleanup:
        subscription?.dispose()
    }

    void 'client filter does not leak request span to subscribing thread with never-completing downstream'() {
        given:
        def filter = new OpenTracingClientFilter(tracer, ConversionService.SHARED, null)
        def request = HttpRequest.GET('/leak-client')
        AtomicReference<Span> spanDuringSubscribe = new AtomicReference<>()
        AtomicReference<Span> spanFromReactorContext = new AtomicReference<>()
        def publisher = filter.doFilter(request, { req ->
            downstream(spanDuringSubscribe, spanFromReactorContext, Mono.never())
        } as ClientFilterChain)

        when:
        def subscription = Mono.from(publisher).subscribe()
        Span requestSpan = request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span).orElseThrow()

        then: 'the downstream subscription ran with the request span active'
        spanDuringSubscribe.get() == requestSpan
        spanFromReactorContext.get() == requestSpan

        and: 'nothing is left installed on the subscribing thread'
        tracer.activeSpan() == null
        !PropagatedContext.exists()

        cleanup:
        subscription?.dispose()
    }

    void 'server filter does not leak scopes when downstream completes on another thread'() {
        given:
        def filter = new OpenTracingServerFilter(tracer, ConversionService.SHARED, null)
        def sink = Sinks.<HttpResponse<?>> one()
        AtomicReference<Span> completingThreadSpan = new AtomicReference<>()
        AtomicReference<Boolean> completingThreadContext = new AtomicReference<>()
        def publisher = filter.doFilter(HttpRequest.GET('/async-server'), { req ->
            downstream(new AtomicReference<>(), new AtomicReference<>(), sink.asMono().publishOn(Schedulers.single()))
        } as ServerFilterChain)

        when:
        def subscription = Mono.from(publisher)
                .doFinally { signal ->
                    completingThreadSpan.set(tracer.activeSpan())
                    completingThreadContext.set(PropagatedContext.exists())
                }
                .subscribe()

        then:
        tracer.activeSpan() == null
        !PropagatedContext.exists()

        when:
        sink.tryEmitValue(HttpResponse.ok())

        then:
        new PollingConditions(timeout: 5).eventually {
            reporter.spans.size() == 1
            completingThreadContext.get() == false
            completingThreadSpan.get() == null
        }
        tracer.activeSpan() == null
        !PropagatedContext.exists()

        cleanup:
        subscription?.dispose()
    }

    void 'client filter does not leak scopes when downstream completes on another thread'() {
        given:
        def filter = new OpenTracingClientFilter(tracer, ConversionService.SHARED, null)
        def sink = Sinks.<HttpResponse<?>> one()
        AtomicReference<Span> completingThreadSpan = new AtomicReference<>()
        AtomicReference<Boolean> completingThreadContext = new AtomicReference<>()
        def publisher = filter.doFilter(HttpRequest.GET('/async-client'), { req ->
            downstream(new AtomicReference<>(), new AtomicReference<>(), sink.asMono().publishOn(Schedulers.single()))
        } as ClientFilterChain)

        when:
        def subscription = Mono.from(publisher)
                .doFinally { signal ->
                    completingThreadSpan.set(tracer.activeSpan())
                    completingThreadContext.set(PropagatedContext.exists())
                }
                .subscribe()

        then:
        tracer.activeSpan() == null
        !PropagatedContext.exists()

        when:
        sink.tryEmitValue(HttpResponse.ok())

        then:
        new PollingConditions(timeout: 5).eventually {
            reporter.spans.size() == 1
            completingThreadContext.get() == false
            completingThreadSpan.get() == null
        }
        tracer.activeSpan() == null
        !PropagatedContext.exists()

        cleanup:
        subscription?.dispose()
    }

    private <T> Publisher<T> downstream(AtomicReference<Span> spanDuringSubscribe,
                                        AtomicReference<Span> spanFromReactorContext,
                                        Mono<T> source) {
        Mono.deferContextual { ctx ->
            spanDuringSubscribe.set(tracer.activeSpan())
            spanFromReactorContext.set(ReactorPropagation.findPropagatedContext(ctx)
                    .flatMap { it.find(OpenTracingPropagationContext) }
                    .map { it.span() }
                    .orElse(null))
            source
        }
    }
}
