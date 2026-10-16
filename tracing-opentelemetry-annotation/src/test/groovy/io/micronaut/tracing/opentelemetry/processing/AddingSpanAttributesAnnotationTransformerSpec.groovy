package io.micronaut.tracing.opentelemetry.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import io.micronaut.tracing.util.TracedMethod

class AddingSpanAttributesAnnotationTransformerSpec extends AbstractTypeElementSpec {

    void 'AddingSpanAttributes is mapped to ContinueSpan'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;

import jakarta.inject.Singleton;

@Singleton
class Test {

    @AddingSpanAttributes
    public void test(String plain, @SpanAttribute("attr") String attribute) {
    }
}
''')
        def method = definition.findMethod('test', String, String).get()
        TracedMethod traced = TracedMethod.of(method)

        expect:
        method.hasAnnotation(ContinueSpan)
        !method.hasAnnotation(NewSpan)
        method.arguments[1].annotationMetadata.stringValue(SpanTag).get() == 'attr'
        traced.precomputed
        !traced.newSpan
        traced.methodName == 'test'
        traced.tagIndexes == [1] as int[]
        traced.tagNames == ['attr'] as String[]
    }

    void 'an AddingSpanAttributes method is intercepted'() {
        given:
        def context = buildContext('test.Test', '''
package test;

import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes;

import jakarta.inject.Singleton;

@Singleton
class Test {

    @AddingSpanAttributes
    public void test() {
    }
}
''')
        def bean = context.getBean(context.classLoader.loadClass('test.Test'))

        expect:
        bean instanceof Intercepted

        cleanup:
        context.close()
    }
}
