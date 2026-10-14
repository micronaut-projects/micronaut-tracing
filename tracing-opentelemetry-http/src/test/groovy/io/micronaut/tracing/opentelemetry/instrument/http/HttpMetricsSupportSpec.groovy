package io.micronaut.tracing.opentelemetry.instrument.http

import io.micronaut.context.BeanContext
import io.micronaut.tracing.opentelemetry.instrument.http.server.MicronautHttpServerTelemetryFactory
import io.micronaut.tracing.opentelemetry.instrument.util.DefaultOperationMetrics
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * The default HTTP metrics are enabled according to the configuration and the meter provider.
 */
class HttpMetricsSupportSpec extends Specification {

    @AutoCleanup
    OpenTelemetrySdk recording = OpenTelemetrySdk.builder()
        .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(InMemoryMetricReader.create()).build())
        .build()

    void 'the default metrics are enabled, also when the Micrometer web metrics filter times the requests too'() {
        given:
        BeanContext beanContext = Mock()
        beanContext.getClassLoader() >> getClass().classLoader

        when:
        def metrics = defaultMetrics(recording, beanContext)

        then:
        1 * beanContext.containsBean(String) >> true
        metrics.enabled
    }

    void 'a failing lookup of the Micrometer web metrics filter is ignored'() {
        given:
        BeanContext beanContext = Mock()
        beanContext.getClassLoader() >> getClass().classLoader
        beanContext.containsBean(String) >> { throw new NoClassDefFoundError('missing') }

        expect:
        defaultMetrics(recording, beanContext).enabled
    }

    void 'the default metrics are disabled when the meter provider does not record'() {
        given:
        BeanContext beanContext = Mock()

        expect:
        !defaultMetrics(OpenTelemetry.noop(), beanContext).enabled
        !DefaultOperationMetrics.recordsMetrics(OpenTelemetry.noop(), 'test')
        DefaultOperationMetrics.recordsMetrics(recording, 'test')
    }

    void 'the deprecated server instrumenter factory method adds the metrics only when they are recorded'() {
        given:
        def factory = new MicronautHttpServerTelemetryFactory()

        expect:
        metricsListeners(factory.instrumenter(recording, [])) == [HttpServerMetrics]
        metricsListeners(factory.instrumenter(OpenTelemetry.noop(), [])).empty
    }

    private static DefaultOperationMetrics defaultMetrics(OpenTelemetry openTelemetry, BeanContext beanContext) {
        // String stands in for the micronaut-micrometer web metrics filter class
        HttpMetricsSupport.defaultMetrics(HttpServerMetrics.get(), null, openTelemetry, 'test', beanContext,
            'server', String.name, 'http.server.request.duration')
    }

    private static List<Class<?>> metricsListeners(Instrumenter<?, ?> instrumenter) {
        def field = Instrumenter.getDeclaredField('operationListeners')
        field.accessible = true
        (field.get(instrumenter) as List).findAll { it instanceof HttpServerMetrics }*.getClass()
    }
}
