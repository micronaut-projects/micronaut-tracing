package io.micronaut.tracing.opentelemetry.instrument.http

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.opentelemetry.instrument.http.server.MicronautHttpServerTelemetryFactory
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class HttpMetricsGatingSpec extends Specification {

    private static final String SPEC_NAME = "HttpMetricsGatingSpec"

    ApplicationContext context
    EmbeddedServer server

    void cleanup() {
        server?.stop()
        context?.close()
    }

    void "HTTP metrics listeners are not registered when metrics are not exported (otel.metrics.exporter=none)"() {
        given:
        start(["tracing.opentelemetry.test.metrics.enabled": false])

        expect:
        metricsListeners("micronautHttpServerTelemetryInstrumenter").empty
        metricsListeners("micronautHttpClientTelemetryInstrumenter").empty

        when: "requests are still traced"
        context.getBean(MetricsClient).hello()

        then:
        new PollingConditions().eventually {
            context.getBean(InMemorySpanExporter).finishedSpanItems.size() == 2
        }
    }

    void "HTTP metrics are recorded when a metric reader is registered"() {
        given:
        start()

        expect:
        metricsListeners("micronautHttpServerTelemetryInstrumenter").size() == 1
        metricsListeners("micronautHttpClientTelemetryInstrumenter").size() == 1

        when:
        context.getBean(MetricsClient).hello()

        then:
        new PollingConditions().eventually {
            def names = context.getBean(InMemoryMetricReader).collectAllMetrics()*.name
            names.contains("http.server.request.duration")
            names.contains("http.client.request.duration")
        }
    }

    void "HTTP metrics are enabled when otel.metrics.exporter is configured"() {
        given:
        start(["tracing.opentelemetry.test.metrics.enabled": false, "otel.metrics.exporter": TestMetricExporterProvider.NAME])

        expect:
        metricsListeners("micronautHttpServerTelemetryInstrumenter")*.getClass() == [HttpServerMetrics]
        metricsListeners("micronautHttpClientTelemetryInstrumenter")*.getClass() == [HttpClientMetrics]
    }

    void "HTTP metrics can be enabled explicitly"() {
        given:
        start([
            "tracing.opentelemetry.test.metrics.enabled"                       : false,
            "tracing.opentelemetry.http.server.metrics.enabled": true
        ])

        expect:
        metricsListeners("micronautHttpServerTelemetryInstrumenter")*.getClass() == [HttpServerMetrics]
        metricsListeners("micronautHttpClientTelemetryInstrumenter").empty
    }

    void "HTTP metrics can be disabled explicitly although a metric reader is registered"() {
        given:
        start([
            "tracing.opentelemetry.http.server.metrics.enabled": false,
            "tracing.opentelemetry.http.client.metrics.enabled": false
        ])

        expect:
        metricsListeners("micronautHttpServerTelemetryInstrumenter").empty
        metricsListeners("micronautHttpClientTelemetryInstrumenter").empty

        when:
        context.getBean(MetricsClient).hello()

        then:
        new PollingConditions().eventually {
            context.getBean(InMemorySpanExporter).finishedSpanItems.size() == 2
        }
        context.getBean(InMemoryMetricReader).collectAllMetrics().every { !it.name.startsWith("http.") }
    }

    void "explicitly contributed OperationMetrics beans are applied when the default metrics are disabled"() {
        given:
        start([
            "tracing.opentelemetry.test.metrics.enabled"                       : false,
            "tracing.opentelemetry.http.server.metrics.enabled": false,
            "custom.server.metrics"                            : true
        ])

        expect:
        metricsListeners("micronautHttpServerTelemetryInstrumenter")*.getClass() == [HttpServerMetrics]
    }

    private void start(Map<String, Object> properties = [:]) {
        context = ApplicationContext.run([
            "spec.name"                 : SPEC_NAME,
            "otel.register.global"      : false,
            "micronaut.application.name": "test-app"
        ] + properties, Environment.TEST)
        server = context.getBean(EmbeddedServer).start()
    }

    private List<Object> metricsListeners(String instrumenterName) {
        def instrumenter = context.getBean(Instrumenter, Qualifiers.byName(instrumenterName))
        def field = Instrumenter.getDeclaredField("operationListeners")
        field.accessible = true
        (field.get(instrumenter) as List).findAll {
            it instanceof HttpServerMetrics || it instanceof HttpClientMetrics
        }
    }

    @Client("/")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static interface MetricsClient {

        @Get("/metrics-gating")
        String hello()
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MetricsController {

        @Get("/metrics-gating")
        String hello() {
            "ok"
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = "custom.server.metrics", value = "true")
    static class CustomMetricsFactory {

        @Singleton
        @MicronautHttpServerTelemetryFactory.Server
        OperationMetrics customServerMetrics() {
            HttpServerMetrics.get()
        }
    }
}
