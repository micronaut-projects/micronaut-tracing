package io.micronaut.tracing.processing

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.tracing.util.TracedMethod

class GroovySpanMetadataVisitorSpec extends AbstractBeanDefinitionSpec {

    void 'span data of a Groovy @NewSpan method is computed at compile time'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.GroovyTraced', '''
package test

import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import jakarta.inject.Singleton

@Singleton
class GroovyTraced {

    @NewSpan("groovy")
    String hello(String plain, @SpanTag("tag") String tagged) {
        return tagged
    }
}
''')

        when:
        ExecutableMethod<?, ?> method = definition.findMethod('hello', String, String).get()
        TracedMethod traced = TracedMethod.of(method)

        then:
        traced.precomputed
        traced.newSpanValue == 'groovy'
        traced.methodName == 'hello'
        traced.tagIndexes == [1] as int[]
        traced.tagNames == ['tag'] as String[]
    }
}
