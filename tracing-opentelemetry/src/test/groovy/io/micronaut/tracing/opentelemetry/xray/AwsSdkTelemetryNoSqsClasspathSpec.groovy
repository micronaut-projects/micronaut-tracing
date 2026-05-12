package io.micronaut.tracing.opentelemetry.xray

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class AwsSdkTelemetryNoSqsClasspathSpec extends Specification {

    void "aws sdk telemetry beans can start without sqs classes"() {
        given:
        ClassLoader noSqsClassLoader = new NoSqsClassLoader(getClass().classLoader)
        ApplicationContext context

        when:
        context = ApplicationContext.builder()
                .classLoader(noSqsClassLoader)
                .start()

        then:
        !isClassPresent(noSqsClassLoader, 'software.amazon.awssdk.services.sqs.SqsClient')

        and:
        context.containsBean(AwsSdkTelemetryProvider)
        context.containsBean(SdkClientBuilderListener)
        !awsSdkTelemetryProviderLeaksSqsTypes()

        cleanup:
        context?.close()
    }

    private static boolean isClassPresent(ClassLoader classLoader, String name) {
        try {
            classLoader.loadClass(name)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
        }
    }

    private static boolean awsSdkTelemetryProviderLeaksSqsTypes() {
        AwsSdkTelemetryProvider.getDeclaredMethods().any { method ->
            method.getReturnType().getName().startsWith('software.amazon.awssdk.services.sqs.') ||
                    method.getParameterTypes().any { it.getName().startsWith('software.amazon.awssdk.services.sqs.') }
        }
    }

    private static final class NoSqsClassLoader extends ClassLoader {
        private static final String SQS_PACKAGE = 'software.amazon.awssdk.services.sqs.'

        NoSqsClassLoader(ClassLoader parent) {
            super(parent)
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(SQS_PACKAGE)) {
                throw new ClassNotFoundException(name)
            }
            return super.loadClass(name, resolve)
        }
    }
}
