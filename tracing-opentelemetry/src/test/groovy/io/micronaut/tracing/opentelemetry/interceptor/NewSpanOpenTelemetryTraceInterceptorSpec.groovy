package io.micronaut.tracing.opentelemetry.interceptor

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.tracing.annotation.NewSpan
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import spock.lang.Specification

class NewSpanOpenTelemetryTraceInterceptorSpec extends Specification {

    void 'span name uses source method name for Kotlin Result return type'() {
        given:
        ClassAndMethod classAndMethod = null
        Instrumenter<ClassAndMethod, Object> instrumenter = Stub() {
            shouldStart(_, _) >> { Context context, ClassAndMethod method ->
                classAndMethod = method
                true
            }
            start(_, _) >> { Context context, ClassAndMethod method -> context }
        }
        def interceptor = new NewSpanOpenTelemetryTraceInterceptor(instrumenter, ConversionService.SHARED)
        def context = Stub(MethodInvocationContext) {
            getAnnotation(NewSpan) >> AnnotationValue.builder(NewSpan).build()
            getDeclaringType() >> KotlinResultService
            getMethodName() >> 'hello-d1pmJ48'
            getReturnType() >> ReturnType.of(String)
            getArguments() >> Argument.ZERO_ARGUMENTS
            getParameterValues() >> ([] as Object[])
            proceed() >> 'hello'
        }

        expect:
        interceptor.intercept(context) == 'hello'
        classAndMethod.methodName() == 'hello'
    }

    void 'custom span name does not include Kotlin mangled method name for Result return type'() {
        given:
        ClassAndMethod classAndMethod = null
        Instrumenter<ClassAndMethod, Object> instrumenter = Stub() {
            shouldStart(_, _) >> { Context context, ClassAndMethod method ->
                classAndMethod = method
                true
            }
            start(_, _) >> { Context context, ClassAndMethod method -> context }
        }
        def interceptor = new NewSpanOpenTelemetryTraceInterceptor(instrumenter, ConversionService.SHARED)
        def context = Stub(MethodInvocationContext) {
            getAnnotation(NewSpan) >> AnnotationValue.builder(NewSpan).value('helloworld').build()
            getDeclaringType() >> KotlinResultService
            getMethodName() >> 'hello-d1pmJ48'
            getReturnType() >> ReturnType.of(String)
            getArguments() >> Argument.ZERO_ARGUMENTS
            getParameterValues() >> ([] as Object[])
            proceed() >> 'hello'
        }

        expect:
        interceptor.intercept(context) == 'hello'
        classAndMethod.methodName() == 'hello#helloworld'
    }

    static class KotlinResultService {
    }
}
