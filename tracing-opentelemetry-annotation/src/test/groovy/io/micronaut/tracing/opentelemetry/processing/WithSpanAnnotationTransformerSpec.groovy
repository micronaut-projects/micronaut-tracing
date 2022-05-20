package io.micronaut.tracing.opentelemetry.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted

class WithSpanAnnotationTransformerSpec extends AbstractTypeElementSpec {

    void 'test map withSpan annotation'() {
        given:
        def context = buildContext('test.Test', '''
package test;

@jakarta.inject.Singleton
class Test {

    @io.opentelemetry.extension.annotations.WithSpan("foo")
    public void test() {
    }
}
''')
        def bean = context.getBean(context.classLoader.loadClass( "test.Test"))

        expect:
        bean instanceof Intercepted

        cleanup:
        context.close()
    }
}
