package io.micronaut.tracing.brave


import io.micronaut.context.ApplicationContext
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.noop.NoopTracer
import spock.lang.Specification
/**
 * @author graemerocher
 * @since 1.0
 */
class BraveTracerFactorySpec extends Specification {

    void 'test brave tracer configuration no endpoint present'() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when: 'The tracer is obtained'
        Tracer tracer = context.getBean(Tracer)

        then: 'It is present'
        tracer instanceof NoopTracer

        cleanup:
        context.close()
    }

    void 'test brace tracer report spans'() {

        given:
        ApplicationContext context = ApplicationContext
                .builder('tracing.zipkin.enabled': true,
                         'tracing.zipkin.sampler.probability': 1)
                .singletons(new TestReporter())
                .start()

        when:
        TestReporter reporter = context.getBean(TestReporter)
        Tracer tracer = context.getBean(Tracer)
        Span span = tracer.buildSpan('test').start()
        Scope scope = tracer.activateSpan(span)

        span.finish()
        scope.close()

        then:
        reporter.spans.size() == 1
        reporter.spans[0].name() == 'test'

        cleanup:
        context.close()
    }
}
