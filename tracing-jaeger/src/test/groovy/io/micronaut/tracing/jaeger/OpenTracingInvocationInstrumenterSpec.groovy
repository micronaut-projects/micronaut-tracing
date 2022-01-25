package io.micronaut.tracing.jaeger

import io.micronaut.context.ApplicationContext
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
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
}
