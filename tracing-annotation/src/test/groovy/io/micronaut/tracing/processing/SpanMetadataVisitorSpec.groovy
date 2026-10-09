package io.micronaut.tracing.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.tracing.util.SpanMetadata
import io.micronaut.tracing.util.TracedMethod

class SpanMetadataVisitorSpec extends AbstractTypeElementSpec {

    void 'span data of @NewSpan and @ContinueSpan methods is computed at compile time'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Traced', '''
package test;

import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import jakarta.inject.Singleton;

@Singleton
class Traced {

    @NewSpan
    public String defaultName(String plain, @SpanTag("custom.tag") String tagged, @SpanTag Integer named) {
        return "";
    }

    @NewSpan("explicit")
    public String explicitName() {
        return "";
    }

    @ContinueSpan
    public void continued(@SpanTag("t") String value) {
    }

    public void notTraced(@SpanTag("t") String value) {
    }
}
''')

        when:
        ExecutableMethod<?, ?> defaultName = definition.findMethod('defaultName', String, String, Integer).get()
        TracedMethod traced = TracedMethod.of(defaultName)

        then:
        defaultName.stringValue(SpanMetadata, SpanMetadata.MEMBER_METHOD).get() == 'defaultName'
        traced.precomputed
        traced.newSpan
        traced.newSpanValue == null
        traced.methodName == 'defaultName'
        traced.tagIndexes == [1, 2] as int[]
        traced.tagNames == ['custom.tag', 'named'] as String[]

        when:
        traced = TracedMethod.of(definition.findMethod('explicitName').get())

        then:
        traced.precomputed
        traced.newSpanValue == 'explicit'
        traced.methodName == 'explicitName'
        traced.tagIndexes.length == 0

        when:
        traced = TracedMethod.of(definition.findMethod('continued', String).get())

        then:
        traced.precomputed
        !traced.newSpan
        traced.methodName == 'continued'
        traced.tagIndexes == [0] as int[]
        traced.tagNames == ['t'] as String[]

        and:
        !definition.findMethod('notTraced', String).isPresent()
    }

    void 'methods of a class annotated with @NewSpan get the span data'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.TracedType', '''
package test;

import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import jakarta.inject.Singleton;

@Singleton
@NewSpan
class TracedType {

    public String hello(@SpanTag("name") String name) {
        return name;
    }
}
''')

        when:
        TracedMethod traced = TracedMethod.of(definition.findMethod('hello', String).get())

        then:
        traced.precomputed
        traced.newSpan
        traced.methodName == 'hello'
        traced.tagNames == ['name'] as String[]
    }
}
