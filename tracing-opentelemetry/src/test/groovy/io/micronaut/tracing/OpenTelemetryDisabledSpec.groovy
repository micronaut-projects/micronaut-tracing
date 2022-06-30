package io.micronaut.tracing

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.tracing.opentelemetry.conf.OpenTelemetryConfiguration
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "micronaut.otel.enabled", value = StringUtils.FALSE)
@MicronautTest(startApplication = false)
class OpenTelemetryDisabledSpec extends Specification {
    @Inject
    BeanContext beanContext
    void "Setting micronaut.otel.enabled false disables Micronaut framework integration with OpenTelemetry"() {
        expect:
        !beanContext.containsBean(OpenTelemetryConfiguration)
    }
}
