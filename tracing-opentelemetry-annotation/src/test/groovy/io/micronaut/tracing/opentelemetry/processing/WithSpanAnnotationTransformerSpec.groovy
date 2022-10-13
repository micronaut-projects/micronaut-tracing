package io.micronaut.tracing.opentelemetry.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted

class WithSpanAnnotationTransformerSpec extends AbstractTypeElementSpec {

    void 'test WithSpan annotation'() {
        given:
        def context = buildContext('test.Test', '''
package test;

import io.opentelemetry.instrumentation.annotations.WithSpan;

import jakarta.inject.Singleton;

@Singleton
class Test {

    @WithSpan("foo")
    public void test() {
    }
}
''')
        def bean = context.getBean(context.classLoader.loadClass("test.Test"))

        expect:
        bean instanceof Intercepted

        cleanup:
        context.close()
    }
}
