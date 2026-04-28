package io.micronaut.tracing.opentelemetry.xray

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.tracing.opentelemetry.DefaultOpenTelemetryFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Scope
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import software.amazon.awssdk.core.SdkRequest
import software.amazon.awssdk.core.interceptor.Context as AwsInterceptorContext
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import jakarta.inject.Inject
import jakarta.inject.Singleton
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@MicronautTest(startApplication = false)
class SdkClientBuilderListenerSpec extends Specification {

    @Inject
    BeanContext beanContext

    void "bean of type SdkClientBuilderListener exists if aws sdk core and opentelemetry-aws-sdk-2.2 dependencies are present"() {
        expect:
        beanContext.containsBean(SdkClientBuilderListener)
    }

    void "aws sdk telemetry uses messaging instrumentation configuration"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'otel.instrumentation.aws-sdk.experimental-span-attributes'                : true,
                'otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging'   : true,
                'otel.instrumentation.messaging.experimental.receive-telemetry.enabled'    : true
        ])

        expect:
        context.getBean(AwsSdkTelemetryConfiguration).experimentalSpanAttributes
        context.getBean(AwsSdkTelemetryConfiguration).experimentalUsePropagatorForMessaging
        context.getBean(MessagingTelemetryConfiguration).enabled
        context.getBean(AwsSdkTelemetryProvider)

        cleanup:
        context.close()
    }

    void "aws sdk telemetry injects configured propagator into sqs message attributes"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                                            : 'SdkClientBuilderListenerSpec',
                'otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging': true
        ])
        ExecutionInterceptor interceptor = context.getBean(AwsSdkTelemetryProvider).newExecutionInterceptor()
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl('https://sqs.us-east-1.amazonaws.com/123456789012/test')
                .messageBody('test')
                .build()

        when:
        SendMessageRequest modifiedRequest = modifyRequestWithCurrentSpan(context.getBean(OpenTelemetry), interceptor, request)

        then:
        modifiedRequest.messageAttributes().containsKey('traceparent')

        cleanup:
        context.close()
    }

    void "aws sdk telemetry does not inject configured propagator into sqs message attributes by default"() {
        given:
        ApplicationContext context = ApplicationContext.run('spec.name': 'SdkClientBuilderListenerSpec')
        ExecutionInterceptor interceptor = context.getBean(AwsSdkTelemetryProvider).newExecutionInterceptor()
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl('https://sqs.us-east-1.amazonaws.com/123456789012/test')
                .messageBody('test')
                .build()

        when:
        SendMessageRequest modifiedRequest = modifyRequestWithCurrentSpan(context.getBean(OpenTelemetry), interceptor, request)

        then:
        !modifiedRequest.messageAttributes().containsKey('traceparent')

        cleanup:
        context.close()
    }

    void "sqs clients are wrapped for message propagation"() {
        given:
        SqsClientFactory.reset()
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                                            : 'SdkClientBuilderListenerSpec',
                'otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging': true,
                'otel.instrumentation.messaging.experimental.receive-telemetry.enabled' : true
        ])

        expect:
        context.containsBean(SqsClientBeanCreatedEventListener)
        context.containsBean(SqsAsyncClientBeanCreatedEventListener)
        !context.getBean(SqsClient).is(SqsClientFactory.sqsClient)
        !context.getBean(SqsAsyncClient).is(SqsClientFactory.sqsAsyncClient)

        cleanup:
        context.close()
        SqsClientFactory.reset()
    }

    void "sqs clients are wrapped when receive telemetry is enabled"() {
        given:
        SqsClientFactory.reset()
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                                           : 'SdkClientBuilderListenerSpec',
                'otel.instrumentation.messaging.experimental.receive-telemetry.enabled': true
        ])

        expect:
        context.containsBean(SqsClientBeanCreatedEventListener)
        context.containsBean(SqsAsyncClientBeanCreatedEventListener)
        !context.getBean(SqsClient).is(SqsClientFactory.sqsClient)
        !context.getBean(SqsAsyncClient).is(SqsClientFactory.sqsAsyncClient)

        cleanup:
        context.close()
        SqsClientFactory.reset()
    }

    void "sqs clients are wrapped when messaging propagator is enabled"() {
        given:
        SqsClientFactory.reset()
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                                            : 'SdkClientBuilderListenerSpec',
                'otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging': true
        ])

        expect:
        context.containsBean(SqsClientBeanCreatedEventListener)
        context.containsBean(SqsAsyncClientBeanCreatedEventListener)
        !context.getBean(SqsClient).is(SqsClientFactory.sqsClient)
        !context.getBean(SqsAsyncClient).is(SqsClientFactory.sqsAsyncClient)

        cleanup:
        context.close()
        SqsClientFactory.reset()
    }

    void "sqs clients are not wrapped by default"() {
        given:
        SqsClientFactory.reset()
        ApplicationContext context = ApplicationContext.run('spec.name': 'SdkClientBuilderListenerSpec')

        expect:
        context.containsBean(SqsClientBeanCreatedEventListener)
        context.containsBean(SqsAsyncClientBeanCreatedEventListener)
        context.getBean(SqsClient).is(SqsClientFactory.sqsClient)
        context.getBean(SqsAsyncClient).is(SqsClientFactory.sqsAsyncClient)

        cleanup:
        context.close()
        SqsClientFactory.reset()
    }

    private static SendMessageRequest modifyRequestWithCurrentSpan(OpenTelemetry openTelemetry,
                                                                   ExecutionInterceptor interceptor,
                                                                   SendMessageRequest request) {
        Span span = openTelemetry.getTracer(SdkClientBuilderListenerSpec.name).spanBuilder('parent').startSpan()
        try (Scope ignored = span.makeCurrent()) {
            return (SendMessageRequest) interceptor.modifyRequest(modifyRequestContext(request), new ExecutionAttributes())
        } finally {
            span.end()
        }
    }

    private static AwsInterceptorContext.ModifyRequest modifyRequestContext(SdkRequest request) {
        [request: { request }] as AwsInterceptorContext.ModifyRequest
    }

    @Factory
    @Requires(property = "spec.name", value = "SdkClientBuilderListenerSpec")
    @Replaces(factory = DefaultOpenTelemetryFactory)
    static class SqsClientFactory {
        static SqsClient sqsClient
        static SqsAsyncClient sqsAsyncClient

        @Singleton
        SqsClient sqsClient() {
            sqsClient = proxy(SqsClient)
        }

        @Singleton
        SqsAsyncClient sqsAsyncClient() {
            sqsAsyncClient = proxy(SqsAsyncClient)
        }

        private static void reset() {
            sqsClient = null
            sqsAsyncClient = null
        }

        private static <T> T proxy(Class<T> type) {
            Proxy.newProxyInstance(type.classLoader, [type] as Class[], { Object proxy, java.lang.reflect.Method method, Object[] args ->
                switch (method.name) {
                    case 'close':
                        return null
                    case 'equals':
                        return proxy.is(args[0])
                    case 'hashCode':
                        return System.identityHashCode(proxy)
                    case 'serviceName':
                        return 'sqs'
                    case 'toString':
                        return "test ${type.simpleName}"
                    default:
                        throw new UnsupportedOperationException(method.name)
                }
            } as InvocationHandler) as T
        }

        @Singleton
        @Primary
        OpenTelemetry openTelemetry() {
            OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder().build())
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build()
        }
    }
}
