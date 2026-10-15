package io.micronaut.tracing.opentracing.interceptor

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.tracing.annotation.NewSpan
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/**
 * The span of a reactive {@code @NewSpan} method finishes exactly once, on the terminal signal, and a
 * {@code Flux} is not truncated to its first element.
 */
class NewSpanPublisherSpec extends Specification {

    Span span = Mock()
    Tracer tracer = Stub() {
        activeSpan() >> null
        buildSpan(_) >> Stub(Tracer.SpanBuilder) {
            start() >> span
        }
        activateSpan(_) >> Stub(Scope)
    }
    NewSpanTraceInterceptor interceptor = new NewSpanTraceInterceptor(tracer, ConversionService.SHARED)

    void 'a Flux span covers every element and finishes once'() {
        given:
        def emitted = new AtomicInteger()
        def flux = Flux.range(1, 3).doOnNext { emitted.incrementAndGet() }
        int emittedAtFinish = -1

        when:
        def result = (interceptor.intercept(context(Flux, flux)) as Flux).collectList().block()

        then:
        result == [1, 2, 3]
        1 * span.finish() >> { emittedAtFinish = emitted.get() }
        emittedAtFinish == 3
    }

    void 'a cancelled Flux finishes the span once'() {
        when:
        def result = (interceptor.intercept(context(Flux, Flux.range(1, 10))) as Flux).take(2).collectList().block()

        then:
        result == [1, 2]
        1 * span.finish()
    }

    void 'a Mono finishes the span once'() {
        when:
        def result = (interceptor.intercept(context(Mono, Mono.just('hello'))) as Mono).block()

        then:
        result == 'hello'
        1 * span.finish()
    }

    void 'a cancelled Mono finishes the span once'() {
        given:
        Sinks.Empty<String> sink = Sinks.empty()

        when:
        def disposable = (interceptor.intercept(context(Mono, sink.asMono())) as Mono).subscribe()

        then:
        0 * span.finish()

        when:
        disposable.dispose()

        then:
        1 * span.finish()
    }

    void 'a failed Mono logs the error and finishes the span once'() {
        when:
        (interceptor.intercept(context(Mono, Mono.error(new IllegalStateException('boom')))) as Mono).block()

        then:
        thrown(IllegalStateException)
        1 * span.log({ Map fields -> fields.message == 'boom' })
        1 * span.finish()
    }

    void 'a failing synchronous method logs the error and finishes the span once'() {
        given:
        MethodInvocationContext context = Stub() {
            getAnnotation(NewSpan) >> AnnotationValue.builder(NewSpan).build()
            getDeclaringType() >> ReactiveService
            getMethodName() >> 'fail'
            getReturnType() >> ReturnType.of(String)
            getArguments() >> Argument.ZERO_ARGUMENTS
            getParameterValues() >> ([] as Object[])
            proceed() >> { throw new IllegalStateException('boom') }
        }

        when:
        interceptor.intercept(context)

        then:
        thrown(IllegalStateException)
        1 * span.log(_)
        1 * span.finish()
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

    static class ReactiveService {
    }
}
