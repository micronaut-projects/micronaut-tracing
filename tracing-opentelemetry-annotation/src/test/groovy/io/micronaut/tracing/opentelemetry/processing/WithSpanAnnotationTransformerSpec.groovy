package io.micronaut.tracing.opentelemetry.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.tracing.util.TracedMethod

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

    void 'the span data of a WithSpan method is computed at compile time'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;

import jakarta.inject.Singleton;

@Singleton
class Test {

    @WithSpan("foo")
    public void test(String plain, @SpanAttribute("attr") String attribute) {
    }
}
''')
        TracedMethod traced = TracedMethod.of(definition.findMethod('test', String, String).get())

        expect:
        traced.precomputed
        traced.newSpan
        traced.newSpanValue == 'foo'
        traced.methodName == 'test'
        traced.tagIndexes == [1] as int[]
        traced.tagNames == ['attr'] as String[]
    }
}
