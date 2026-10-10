package io.micronaut.tracing.opentelemetry.inspector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer
import io.micronaut.tracing.opentelemetry.inspector.endpoint.TracesEndpoint
import io.opentelemetry.api.OpenTelemetry
import spock.lang.Specification
import spock.lang.Unroll

class TraceInspectorEnablementSpec extends Specification {

    static final String INSPECTOR_ENABLED = 'tracing.opentelemetry.inspector.enabled'
    static final String OTEL_ENABLED = 'micronaut.otel.enabled'

    @Unroll
    void "the inspector is #description"(Map<String, Object> properties, List<String> environments, boolean enabled) {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties(properties)
            .environments(environments as String[])
            .start()

        expect:
        context.containsBean(TraceInspector) == enabled
        context.containsBean(TraceInspectorConfiguration) == enabled
        context.containsBean(OpenTelemetryBuilderCustomizer, Qualifiers.byName("traceInspector")) == enabled
        context.containsBean(TracesEndpoint) == enabled

        cleanup:
        context.close()

        where:
        description                                 | properties                                          | environments              | enabled
        'disabled by default'                       | [:]                                                 | []                        | false
        'enabled by default in the dev environment' | [:]                                                 | [Environment.DEVELOPMENT] | true
        'disabled in dev with enabled=false'        | ['tracing.opentelemetry.inspector.enabled': false]  | [Environment.DEVELOPMENT] | false
        'enabled with enabled=true'                 | ['tracing.opentelemetry.inspector.enabled': true]   | []                        | true
        'disabled with OpenTelemetry disabled'      | [(INSPECTOR_ENABLED): true, (OTEL_ENABLED): false]  | [Environment.DEVELOPMENT] | false
    }

    void "the configuration has bounded defaults"() {
        given:
        ApplicationContext context = ApplicationContext.run(['tracing.opentelemetry.inspector.enabled': true])
        TraceInspectorConfiguration configuration = context.getBean(TraceInspectorConfiguration)

        expect:
        configuration.enabled
        configuration.maxTraces == 200
        configuration.maxPendingTraces == 100
        configuration.maxSpansPerTrace == 2000
        configuration.maxAttributeLength == 1024

        cleanup:
        context.close()
    }

    void "spans of the OpenTelemetry bean are recorded only when enabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(Environment.DEVELOPMENT)

        when:
        context.getBean(OpenTelemetry).getTracer("test").spanBuilder("dev").startSpan().end()

        then:
        context.getBean(TraceInspector).traces()*.name() == ["dev"]

        cleanup:
        context.close()
    }
}
