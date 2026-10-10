package io.micronaut.tracing.util

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.type.Argument
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import spock.lang.Specification

class TracedMethodSpec extends Specification {

    void 'without compile time metadata the span data is computed from the method at runtime'() {
        given:
        def tagged = new MutableAnnotationMetadata()
        tagged.addDeclaredAnnotation(SpanTag.name, [value: 'custom.tag'])
        def taggedWithoutName = new MutableAnnotationMetadata()
        taggedWithoutName.addDeclaredAnnotation(SpanTag.name, [:])
        ExecutableMethod method = Stub() {
            getAnnotation(NewSpan) >> AnnotationValue.builder(NewSpan).value('op').build()
            getAnnotation(SpanMetadata) >> null
            getMethodName() >> 'hello-d1pmJ48'
            getArguments() >> ([
                    Argument.of(String, 'plain'),
                    Argument.of(String, 'first', tagged),
                    Argument.of(String, 'second', taggedWithoutName)
            ] as Argument[])
        }

        when:
        TracedMethod traced = TracedMethod.of(method)

        then:
        !traced.precomputed
        traced.newSpan
        traced.newSpanValue == 'op'
        traced.methodName == 'hello'
        traced.tagIndexes == [1, 2] as int[]
        traced.tagNames == ['custom.tag', 'second'] as String[]
    }

    void 'the runtime fallback cannot recognise a Kotlin mangling hash made of lowercase letters only'() {
        // the compile time metadata uses the source name, see SpanMetadataVisitor
        expect:
        MethodNameFormatter.format('lookup-gjfpqcc') == 'lookup-gjfpqcc'
    }

    void 'the source method name is the compile time one when present'() {
        given:
        ExecutableMethod precomputed = Stub() {
            getAnnotation(SpanMetadata) >> AnnotationValue.builder(SpanMetadata).member(SpanMetadata.MEMBER_METHOD, 'lookup').build()
            getMethodName() >> 'lookup-gjfpqcc'
        }
        ExecutableMethod runtime = Stub() {
            getAnnotation(SpanMetadata) >> null
            getMethodName() >> 'hello-d1pmJ48'
        }

        expect:
        TracedMethod.methodName(precomputed) == 'lookup'
        TracedMethod.methodName(runtime) == 'hello'
    }
}
