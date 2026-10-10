package io.micronaut.tracing.opentelemetry.sampler

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.samplers.Sampler
import jakarta.inject.Singleton
import spock.lang.Specification

class SamplerConfigurationSpec extends Specification {

    void "without configuration the sampler configured by otel.traces.sampler is used unchanged"() {
        expect:
        sampler([:]) == "ParentBased{root:AlwaysOnSampler,remoteParentSampled:AlwaysOnSampler,remoteParentNotSampled:AlwaysOffSampler,localParentSampled:AlwaysOnSampler,localParentNotSampled:AlwaysOffSampler}"
        sampler(['otel.traces.sampler': 'traceidratio', 'otel.traces.sampler.arg': '0.25']) == "TraceIdRatioBased{0.250000}"
    }

    void "the rate limit wraps the sampler configured by otel.traces.sampler"() {
        when:
        def description = sampler([
            'tracing.opentelemetry.sampler.rate-limit.traces-per-second': '2.5',
            'otel.traces.sampler'                                      : 'parentbased_traceidratio',
            'otel.traces.sampler.arg'                                  : '0.5'
        ])

        then:
        description.startsWith("MicronautRootSampler{rules=[], tracesPerSecond=2.5, burst=3, delegate=ParentBased{root:TraceIdRatioBased{0.500000}")
    }

    void "the rules are bound from a list in order"() {
        when:
        def description = sampler([
            'tracing.opentelemetry.sampler.rules[0].path' : '/health.*',
            'tracing.opentelemetry.sampler.rules[1].route': '/books/\\{id\\}',
            'tracing.opentelemetry.sampler.rules[1].ratio': '0.1',
            'tracing.opentelemetry.sampler.rate-limit.traces-per-second': '10',
            'tracing.opentelemetry.sampler.rate-limit.burst': '20'
        ])

        then:
        description.startsWith("MicronautRootSampler{rules=[{path=/health.*, route=null, sampler=AlwaysOffSampler}, {path=null, route=/books/\\{id\\}, sampler=TraceIdRatioBased{0.100000}}], tracesPerSecond=10.0, burst=20, delegate=ParentBased{root:AlwaysOnSampler")
    }

    void "a Sampler bean of the application wins over the rate limit and the rules"() {
        expect:
        sampler([
            'spec.name'                                                : 'SamplerConfigurationSpec',
            'tracing.opentelemetry.sampler.rate-limit.traces-per-second': '1',
            'tracing.opentelemetry.sampler.rules[0].path'              : '/health'
        ]) == "AlwaysOffSampler"
    }

    void "an invalid configuration fails at startup"() {
        when:
        sampler(['tracing.opentelemetry.sampler.rules[0].path': '/(a'])

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains("Invalid pattern of tracing.opentelemetry.sampler.rules[0]")
    }

    private static String sampler(Map<String, Object> properties) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            def sdk = (OpenTelemetrySdk) context.getBean(OpenTelemetry)
            return sdk.sdkTracerProvider.sampler.description
        } finally {
            context.close()
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "SamplerConfigurationSpec")
    static class SamplerFactory {

        @Singleton
        Sampler sampler() {
            Sampler.alwaysOff()
        }
    }
}
