package io.micronaut.tracing.opentelemetry.instrument.http

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.opentelemetry.instrument.http.client.MicronautHttpClientTelemetryFactory
import io.micronaut.tracing.opentelemetry.instrument.http.server.MicronautHttpServerTelemetryFactory
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.semconv.HttpAttributes
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicInteger

class MicronautHttpTelemetryFactorySpec extends Specification {

    private static final String SPEC_NAME = "MicronautHttpTelemetryFactorySpec"
    private static final SpanContext CLIENT_LINKED_CONTEXT = SpanContext.create(
        "00000000000000000000000000000001",
        "0000000000000001",
        TraceFlags.getSampled(),
        TraceState.getDefault()
    )
    private static final SpanContext SERVER_LINKED_CONTEXT = SpanContext.create(
        "00000000000000000000000000000002",
        "0000000000000002",
        TraceFlags.getSampled(),
        TraceState.getDefault()
    )

    ApplicationContext context

    void cleanup() {
        context?.close()
        CustomHttpTelemetryFactory.reset()
    }

    void "starts ApplicationContext with default http telemetry instrumenters"() {
        when:
        context = startContext()

        then:
        context.getBean(Instrumenter, Qualifiers.byName("micronautHttpClientTelemetryInstrumenter"))
        context.getBean(Instrumenter, Qualifiers.byName("micronautHttpServerTelemetryInstrumenter"))
    }

    void "uses replacement client and server span name extractors"() {
        given:
        context = startContext()
        def server = context.getBean(EmbeddedServer).start()
        def client = context.getBean(TestClient)

        when:
        client.name()

        then:
        new PollingConditions().eventually {
            def spans = context.getBean(InMemorySpanExporter).finishedSpanItems
            spans.find { it.kind == SpanKind.CLIENT }.name == "custom-client-span"
            spans.find { it.kind == SpanKind.SERVER }.name == "custom-server-span"
        }

        cleanup:
        server?.stop()
    }

    void "applies server list contributors and preserves route attribute"() {
        given:
        context = startContext()
        def server = context.getBean(EmbeddedServer).start()
        def client = context.getBean(TestClient)

        when:
        client.route("abc")

        then:
        new PollingConditions().eventually {
            def spans = context.getBean(InMemorySpanExporter).finishedSpanItems
            def clientSpan = spans.find { it.kind == SpanKind.CLIENT }
            def serverSpan = spans.find { it.kind == SpanKind.SERVER }
            clientSpan
            serverSpan
            clientSpan.links*.spanContext == [CLIENT_LINKED_CONTEXT]
            serverSpan.links*.spanContext == [SERVER_LINKED_CONTEXT]
            serverSpan.attributes.get(HttpAttributes.HTTP_ROUTE) == "/route/{id}"
            CustomHttpTelemetryFactory.clientListenerStart.get() == 1
            CustomHttpTelemetryFactory.clientListenerEnd.get() == 1
            CustomHttpTelemetryFactory.serverListenerStart.get() == 1
            CustomHttpTelemetryFactory.serverListenerEnd.get() == 1
        }

        cleanup:
        server?.stop()
    }

    private static ApplicationContext startContext() {
        ApplicationContext.run([
            "spec.name"                 : SPEC_NAME,
            "otel.register.global"      : false,
            "micronaut.application.name": "test-app"
        ], Environment.TEST)
    }

    @Client("/")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static interface TestClient {

        @Get("/name")
        String name()

        @Get("/route/{id}")
        String route(String id)
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TestController {

        @Get("/name")
        String name() {
            "ok"
        }

        @Get("/route/{id}")
        String route(String id) {
            id
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Factory
    static class CustomHttpTelemetryFactory {

        static AtomicInteger clientListenerStart = new AtomicInteger()
        static AtomicInteger clientListenerEnd = new AtomicInteger()
        static AtomicInteger serverListenerStart = new AtomicInteger()
        static AtomicInteger serverListenerEnd = new AtomicInteger()

        static void reset() {
            clientListenerStart.set(0)
            clientListenerEnd.set(0)
            serverListenerStart.set(0)
            serverListenerEnd.set(0)
        }

        @MicronautHttpClientTelemetryFactory.Client
        @Singleton
        @Replaces(bean = SpanNameExtractor, factory = MicronautHttpClientTelemetryFactory, qualifier = MicronautHttpClientTelemetryFactory.Client)
        SpanNameExtractor<MutableHttpRequest<Object>> clientSpanNameExtractor() {
            { MutableHttpRequest<Object> ignored -> "custom-client-span" } as SpanNameExtractor<MutableHttpRequest<Object>>
        }

        @MicronautHttpServerTelemetryFactory.Server
        @Singleton
        @Replaces(bean = SpanNameExtractor, factory = MicronautHttpServerTelemetryFactory, qualifier = MicronautHttpServerTelemetryFactory.Server)
        SpanNameExtractor<HttpRequest<Object>> serverSpanNameExtractor() {
            { HttpRequest<Object> ignored -> "custom-server-span" } as SpanNameExtractor<HttpRequest<Object>>
        }

        @MicronautHttpClientTelemetryFactory.Client
        @Singleton
        OperationListener clientOperationListener() {
            new OperationListener() {
                @Override
                Context onStart(Context context, Attributes startAttributes, long startNanos) {
                    clientListenerStart.incrementAndGet()
                    context
                }

                @Override
                void onEnd(Context context, Attributes endAttributes, long endNanos) {
                    clientListenerEnd.incrementAndGet()
                }
            }
        }

        @MicronautHttpServerTelemetryFactory.Server
        @Singleton
        OperationListener serverOperationListener() {
            new OperationListener() {
                @Override
                Context onStart(Context context, Attributes startAttributes, long startNanos) {
                    serverListenerStart.incrementAndGet()
                    context
                }

                @Override
                void onEnd(Context context, Attributes endAttributes, long endNanos) {
                    serverListenerEnd.incrementAndGet()
                }
            }
        }

        @MicronautHttpClientTelemetryFactory.Client
        @Singleton
        SpanLinksExtractor<MutableHttpRequest<Object>> clientSpanLinksExtractor() {
            { SpanLinksBuilder spanLinks, Context parentContext, MutableHttpRequest<Object> request ->
                spanLinks.addLink(CLIENT_LINKED_CONTEXT)
            } as SpanLinksExtractor<MutableHttpRequest<Object>>
        }

        @MicronautHttpServerTelemetryFactory.Server
        @Singleton
        SpanLinksExtractor<HttpRequest<Object>> serverSpanLinksExtractor() {
            { SpanLinksBuilder spanLinks, Context parentContext, HttpRequest<Object> request ->
                spanLinks.addLink(SERVER_LINKED_CONTEXT)
            } as SpanLinksExtractor<HttpRequest<Object>>
        }
    }
}
