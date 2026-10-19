package io.micronaut.tracing.opentelemetry.instrument.redis

import io.micronaut.context.ApplicationContext
import io.opentelemetry.api.OpenTelemetry
import spock.lang.Specification

/**
 * The Lettuce telemetry is enabled by default and can be disabled.
 */
class LettuceTelemetryConfigurationSpec extends Specification {

    void 'the Lettuce telemetry is enabled by default and builds a telemetry instance'() {
        given:
        def configuration = new LettuceTelemetryConfiguration(OpenTelemetry.noop())

        expect:
        configuration.enabled
        configuration.build() != null

        when:
        configuration.enabled = false

        then:
        !configuration.enabled
    }

    void 'disabling the Lettuce telemetry disables the instrumentation'() {
        given:
        ApplicationContext context = ApplicationContext.run(
            'otel.instrumentation.lettuce.enabled': false,
            'otel.traces.exporter': 'none',
            'otel.metrics.exporter': 'none',
            'otel.logs.exporter': 'none'
        )

        expect:
        !context.containsBean(LettuceTelemetryConfiguration)

        cleanup:
        context.close()
    }
}
