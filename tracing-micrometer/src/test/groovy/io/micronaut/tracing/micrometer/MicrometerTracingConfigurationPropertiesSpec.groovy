package io.micronaut.tracing.micrometer

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class MicrometerTracingConfigurationPropertiesSpec extends Specification {

    void 'test micrometer tracing configuration defaults'() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        MicrometerTracingConfigurationProperties configuration = context.getBean(MicrometerTracingConfigurationProperties)

        then:
        configuration.enabled
        configuration.baggage.remoteFields.empty
        configuration.baggage.correlationFields.empty

        cleanup:
        context.close()
    }

    void 'test micrometer tracing baggage configuration'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'tracing.micrometer.baggage.remote-fields': ['x-request-id', 'tenant'],
                'tracing.micrometer.baggage.correlation-fields': ['tenant']
        )

        when:
        MicrometerTracingConfigurationProperties configuration = context.getBean(MicrometerTracingConfigurationProperties)

        then:
        configuration.baggage.remoteFields == ['x-request-id', 'tenant']
        configuration.baggage.correlationFields == ['tenant']

        cleanup:
        context.close()
    }

    void 'test micrometer tracing can be disabled'() {
        given:
        ApplicationContext context = ApplicationContext.run('tracing.micrometer.enabled': false)

        expect:
        !context.containsBean(MicrometerTracingConfigurationProperties)

        cleanup:
        context.close()
    }
}
