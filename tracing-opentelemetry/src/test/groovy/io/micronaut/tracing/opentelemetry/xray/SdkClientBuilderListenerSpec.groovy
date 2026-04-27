package io.micronaut.tracing.opentelemetry.xray

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import spock.lang.Specification

import java.lang.reflect.Field
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

        when:
        Object captureExperimentalSpanAttributes = field(context.getBean(AwsSdkTelemetry), 'captureExperimentalSpanAttributes')
        Object messagingPropagator = field(context.getBean(AwsSdkTelemetry), 'messagingPropagator')

        then:
        assertTelemetryConfigured(captureExperimentalSpanAttributes, messagingPropagator)

        cleanup:
        context.close()
    }

    void "sqs clients are wrapped for message propagation"() {
        given:
        SqsClientFactory.reset()
        ApplicationContext context = ApplicationContext.run('spec.name': 'SdkClientBuilderListenerSpec')

        expect:
        context.containsBean(SqsClientBeanCreatedEventListener)
        context.containsBean(SqsAsyncClientBeanCreatedEventListener)
        !context.getBean(SqsClient).is(SqsClientFactory.sqsClient)
        !context.getBean(SqsAsyncClient).is(SqsClientFactory.sqsAsyncClient)

        cleanup:
        context.close()
        SqsClientFactory.reset()
    }

    private static Object field(Object bean, String name) {
        Field field = AwsSdkTelemetry.class.getDeclaredField(name)
        field.accessible = true
        field.get(bean)
    }

    private static boolean assertTelemetryConfigured(Object captureExperimentalSpanAttributes, Object messagingPropagator) {
        if (captureExperimentalSpanAttributes != Boolean.TRUE) {
            throw new AssertionError("captureExperimentalSpanAttributes was ${captureExperimentalSpanAttributes}")
        }
        if (messagingPropagator == null) {
            throw new AssertionError("messagingPropagator was null")
        }
        true
    }

    @Factory
    @Requires(property = "spec.name", value = "SdkClientBuilderListenerSpec")
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
    }
}
