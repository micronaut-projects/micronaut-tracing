package io.micronaut.tracing.jaeger

import io.micronaut.context.ApplicationContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.tracing.opentracing.OpenTracingPropagationContext
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * Regression test: when executing certain reactive operations the ScopeManager's internal state can become permanently
 * corrupted resulting in incorrect tracing.
 *
 * @author lgathy
 */
class OpenTracingInvocationInstrumenterSpec extends Specification {

    void "test regression of corrupted ScopeManager state"() {
        given: 'Jaeger tracer is enabled'
        ApplicationContext context = ApplicationContext.run('tracing.jaeger.enabled': true)
        Tracer tracer = context.getBean(Tracer)
        String[] words = ['one', 'two', 'three']

        expect: 'no active span'
        tracer.activeSpan() == null

        when: 'reactive operations are executed inside a span'
        Span rootSpan = tracer.buildSpan('root').start()
        Scope scope = tracer.activateSpan(rootSpan)
        String combined = Flux
                .merge(words.collect { Mono.just(it).flux() })
                .reduce { a, b -> "$a, $b" }
                .block()
        scope.close()
        rootSpan.finish()

        then: 'there should be no active span after it was finished'
        combined.split(", ").sort() == words.sort()
        tracer.activeSpan() == null
    }

    void "test propagated OpenTracing context keeps one active span element"() {
        given: 'Jaeger tracer is enabled'
        ApplicationContext context = ApplicationContext.run('tracing.jaeger.enabled': true)
        Tracer tracer = context.getBean(Tracer)
        Map previousMdc = MDC.getCopyOfContextMap()
        MDC.put('traceId', 'previous-trace')
        Span firstSpan = tracer.buildSpan('first').start()
        Span secondSpan = tracer.buildSpan('second').start()
        PropagatedContext firstContext = OpenTracingPropagationContext.withSpan(PropagatedContext.getOrEmpty(), tracer, firstSpan)
        PropagatedContext secondContext = OpenTracingPropagationContext.withSpan(firstContext, tracer, secondSpan)
        PropagatedContext duplicatedContext = PropagatedContext.getOrEmpty()
                .plus(new OpenTracingPropagationContext(tracer, firstSpan))
                .plus(new OpenTracingPropagationContext(tracer, secondSpan))
        PropagatedContext normalizedContext = OpenTracingPropagationContext.withSpan(duplicatedContext, tracer, secondSpan)

        expect: 'no active span'
        tracer.activeSpan() == null
        MDC.get('traceId') == 'previous-trace'
        firstContext.findAll(OpenTracingPropagationContext).toList().size() == 1
        firstContext.get(OpenTracingPropagationContext).span() == firstSpan
        secondContext.findAll(OpenTracingPropagationContext).toList().size() == 1
        secondContext.get(OpenTracingPropagationContext).span() == secondSpan
        duplicatedContext.findAll(OpenTracingPropagationContext).toList().size() == 2
        normalizedContext.findAll(OpenTracingPropagationContext).toList().size() == 1
        normalizedContext.get(OpenTracingPropagationContext).span() == secondSpan

        when: 'the first context is activated'
        PropagatedContext.Scope firstScope = firstContext.propagate()

        then:
        tracer.activeSpan() == firstSpan
        MDC.get('traceId') == firstSpan.context().toTraceId()

        when: 'the OpenTracing context element is replaced by another span'
        PropagatedContext.Scope secondScope = secondContext.propagate()

        then:
        tracer.activeSpan() == secondSpan
        MDC.get('traceId') == secondSpan.context().toTraceId()

        when: 'contexts are restored in stack order'
        secondScope.close()

        then:
        tracer.activeSpan() == firstSpan
        MDC.get('traceId') == firstSpan.context().toTraceId()

        when:
        firstScope.close()

        then:
        tracer.activeSpan() == null
        MDC.get('traceId') == 'previous-trace'

        cleanup:
        if (previousMdc == null) {
            MDC.clear()
        } else {
            MDC.setContextMap(previousMdc)
        }
        secondSpan.finish()
        firstSpan.finish()
        context.close()
    }
}
