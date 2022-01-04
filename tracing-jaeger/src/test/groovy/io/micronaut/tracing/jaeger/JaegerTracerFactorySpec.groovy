package io.micronaut.tracing.jaeger

import io.jaegertracing.internal.JaegerTracer
import io.micronaut.context.ApplicationContext
import io.opentracing.Tracer
import io.opentracing.noop.NoopTracer
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class JaegerTracerFactorySpec extends Specification {

    void 'test enable Jaeger tracing'() {
        when:
        ApplicationContext ctx = ApplicationContext.run('tracing.jaeger.enabled': 'true')

        then:
        ctx.getBean(Tracer) instanceof JaegerTracer

        cleanup:
        ctx.close()
    }

    void 'test default tracing'() {
        when:
        ApplicationContext ctx = ApplicationContext.run()

        then:
        ctx.getBean(Tracer) instanceof NoopTracer

        cleanup:
        ctx.close()
    }
}
