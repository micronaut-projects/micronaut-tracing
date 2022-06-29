package io.micronaut.tracing.opentelemetry.conf

import io.micronaut.context.BeanContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class OpenTelemetryConfigurationSpec extends Specification {
    @Inject
    BeanContext beanContext
    void "Micronaut framework integration with OpenTelemetry is enabled by default"() {
        expect:
        beanContext.containsBean(OpenTelemetryConfiguration)
        beanContext.getBean(OpenTelemetryConfiguration).isEnabled()
    }
}
