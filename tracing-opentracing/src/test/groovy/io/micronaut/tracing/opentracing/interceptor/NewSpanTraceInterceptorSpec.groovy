package io.micronaut.tracing.opentracing.interceptor

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.tracing.annotation.NewSpan
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import spock.lang.Specification

class NewSpanTraceInterceptorSpec extends Specification {

    void 'span name and method tag use source method name for Kotlin Result return type'() {
        given:
        String operationName = null
        Span span = Mock()
        Scope scope = Mock()
        Tracer.SpanBuilder spanBuilder = Stub() {
            start() >> span
        }
        Tracer tracer = Stub() {
            activeSpan() >> null
            buildSpan(_) >> { String name ->
                operationName = name
                spanBuilder
            }
            activateSpan(span) >> scope
        }
        def interceptor = new NewSpanTraceInterceptor(tracer, ConversionService.SHARED)
        def context = Stub(MethodInvocationContext) {
            getAnnotation(NewSpan) >> AnnotationValue.builder(NewSpan).build()
            getDeclaringType() >> KotlinResultService
            getMethodName() >> 'hello-d1pmJ48'
            getReturnType() >> ReturnType.of(String)
            getArguments() >> Argument.ZERO_ARGUMENTS
            getParameterValues() >> ([] as Object[])
            proceed() >> 'hello'
        }

        when:
        def result = interceptor.intercept(context)

        then:
        result == 'hello'
        operationName == 'KotlinResultService.hello'
        2 * span.setTag(AbstractTraceInterceptor.CLASS_TAG, 'KotlinResultService') >> span
        2 * span.setTag(AbstractTraceInterceptor.METHOD_TAG, 'hello') >> span
        1 * span.finish()
        1 * scope.close()
    }

    static class KotlinResultService {
    }
}
