package io.micronaut.tracing.opentelemetry.interceptor

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.tracing.annotation.NewSpan
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/**
 * The span of a reactive {@code @NewSpan} method ends exactly once, on the terminal signal.
 */
class NewSpanPublisherSpec extends Specification {

    Instrumenter<ClassAndMethod, Object> instrumenter = Mock()
    NewSpanOpenTelemetryTraceInterceptor interceptor = new NewSpanOpenTelemetryTraceInterceptor(instrumenter, ConversionService.SHARED)

    void 'a Mono with a value ends the span once, with the value'() {
        when:
        def result = (interceptor.intercept(context(Mono, Mono.just('hello'))) as Mono).block()

        then:
        result == 'hello'
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        1 * instrumenter.end(_, _, 'hello', null)
        0 * instrumenter.end(*_)
    }

    void 'an empty Mono ends the span once'() {
        when:
        def result = (interceptor.intercept(context(Mono, Mono.empty())) as Mono).block()

        then:
        result == null
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        1 * instrumenter.end(_, _, null, null)
        0 * instrumenter.end(*_)
    }

    void 'a failed Mono ends the span once with the error'() {
        given:
        def error = new IllegalStateException('boom')

        when:
        (interceptor.intercept(context(Mono, Mono.error(error))) as Mono).block()

        then:
        thrown(IllegalStateException)
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        1 * instrumenter.end(_, _, null, error)
        0 * instrumenter.end(*_)
    }

    void 'a cancelled Mono ends the span once'() {
        given:
        Sinks.Empty<String> sink = Sinks.empty()

        when:
        def disposable = (interceptor.intercept(context(Mono, sink.asMono())) as Mono).subscribe()

        then:
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        0 * instrumenter.end(*_)

        when:
        disposable.dispose()

        then:
        1 * instrumenter.end(_, _, null, null)
        0 * instrumenter.end(*_)
    }

    void 'a Flux span covers every element and ends once on completion'() {
        given:
        def emitted = new AtomicInteger()
        def flux = Flux.range(1, 3).doOnNext { emitted.incrementAndGet() }
        int emittedAtEnd = -1

        when:
        def result = (interceptor.intercept(context(Flux, flux)) as Flux).collectList().block()

        then:
        result == [1, 2, 3]
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        1 * instrumenter.end(_, _, null, null) >> { emittedAtEnd = emitted.get() }
        0 * instrumenter.end(*_)
        emittedAtEnd == 3
    }

    void 'a cancelled Flux ends the span once'() {
        when:
        def result = (interceptor.intercept(context(Flux, Flux.range(1, 10))) as Flux).take(2).collectList().block()

        then:
        result == [1, 2]
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        1 * instrumenter.end(_, _, null, null)
        0 * instrumenter.end(*_)
    }

    void 'a failed Flux ends the span once with the error'() {
        given:
        def error = new IllegalStateException('boom')

        when:
        (interceptor.intercept(context(Flux, Flux.just(1).concatWith(Flux.error(error)))) as Flux).collectList().block()

        then:
        thrown(IllegalStateException)
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        1 * instrumenter.end(_, _, null, error)
        0 * instrumenter.end(*_)
    }

    void 'a synchronous method ends the span once, also when it fails'() {
        given:
        def error = new IllegalStateException('boom')

        when:
        def result = interceptor.intercept(context(String, 'hello'))

        then:
        result == 'hello'
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        1 * instrumenter.end(_, _, 'hello', null)
        0 * instrumenter.end(*_)

        when:
        interceptor.intercept(failingContext(error))

        then:
        thrown(IllegalStateException)
        1 * instrumenter.shouldStart(_, _) >> true
        1 * instrumenter.start(_, _) >> { Context parent, ClassAndMethod method -> parent }
        1 * instrumenter.end(_, _, null, error)
        0 * instrumenter.end(*_)
    }

    void 'the method is not called through the reactive path when the span is not sampled'() {
        when:
        def result = (interceptor.intercept(context(Mono, Mono.just('hello'))) as Mono).block()

        then:
        result == 'hello'
        1 * instrumenter.shouldStart(_, _) >> false
        0 * instrumenter.start(*_)
        0 * instrumenter.end(*_)
    }

    private MethodInvocationContext context(Class<?> returnType, Object result) {
        Stub(MethodInvocationContext) {
            getAnnotation(NewSpan) >> AnnotationValue.builder(NewSpan).build()
            getDeclaringType() >> ReactiveService
            getMethodName() >> 'call'
            getReturnType() >> ReturnType.of(returnType)
            getArguments() >> Argument.ZERO_ARGUMENTS
            getParameterValues() >> ([] as Object[])
            proceed() >> result
        }
    }

    private MethodInvocationContext failingContext(Throwable error) {
        Stub(MethodInvocationContext) {
            getAnnotation(NewSpan) >> AnnotationValue.builder(NewSpan).build()
            getDeclaringType() >> ReactiveService
            getMethodName() >> 'fail'
            getReturnType() >> ReturnType.of(String)
            getArguments() >> Argument.ZERO_ARGUMENTS
            getParameterValues() >> ([] as Object[])
            proceed() >> { throw error }
        }
    }

    static class ReactiveService {
    }
}
