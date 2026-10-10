package io.micronaut.tracing.opentelemetry.instrument.http

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.annotation.NewSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.MDC
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ExecutorService

/**
 * The trace context is in the MDC while a request is handled, across Micronaut's context propagation.
 */
@Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/144')
class OpenTelemetryMdcHttpSpec extends Specification {

    static final String SPEC_NAME = 'OpenTelemetryMdcHttpSpec'

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
        'spec.name': SPEC_NAME,
        'otel.register.global': false,
        'micronaut.application.name': 'test-app',
        'otel.exclusions[0]': '/mdc/untraced',
        // single threads: the follow-up checks run on the threads that handled the traced requests
        'micronaut.netty.event-loops.default.num-threads': 1,
        'micronaut.executors.io.type': 'fixed',
        'micronaut.executors.io.n-threads': 1
    )

    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    PollingConditions conditions = new PollingConditions()

    void 'the trace context of the server span is in the MDC of the request handler'() {
        when:
        Map<String, String> mdc = get('/mdc/sync')

        then:
        mdc.trace_id
        mdc.span_id
        mdc.trace_flags == mdc.current_trace_flags && (Integer.parseInt(mdc.trace_flags, 16) & 1) == 1
        mdc.trace_id == mdc.current_trace_id
        mdc.span_id == mdc.current_span_id

        and: 'the ids are those of the server span'
        conditions.eventually {
            def server = exporter().finishedSpanItems.find { it.kind == SpanKind.SERVER && it.name == 'GET /mdc/sync' }
            assert server
            assert server.traceId == mdc.trace_id
            assert server.spanId == mdc.span_id
        }

        and: 'the event loop thread is left without the trace context'
        get('/mdc/untraced') == [:]
    }

    void 'the span of a @NewSpan method is in the MDC'() {
        when:
        Map<String, String> mdc = get('/mdc/newSpan')

        then:
        mdc.trace_id == mdc.current_trace_id
        mdc.span_id == mdc.current_span_id
        mdc.span_id != mdc.server_span_id
        mdc.after_span_id == mdc.server_span_id
    }

    void 'the trace context is in the MDC after an executor hop and the executor thread is left clean'() {
        when:
        Map<String, String> mdc = get('/mdc/blocking')

        then:
        mdc.trace_id
        mdc.trace_id == mdc.current_trace_id
        mdc.span_id == mdc.current_span_id

        when: 'the single IO thread runs a task outside of a request'
        ExecutorService io = context.getBean(ExecutorService, Qualifiers.byName(TaskExecutors.IO))
        Map<String, String> leftOver = io.submit({ MdcController.snapshot() } as java.util.concurrent.Callable).get()

        then:
        leftOver == [:]
    }

    void 'the trace context of the request is in the MDC after a reactive HTTP client hop'() {
        when:
        Map<String, String> mdc = get('/mdc/reactive')

        then:
        mdc.trace_id
        mdc.trace_id == mdc.server_trace_id
        mdc.span_id == mdc.server_span_id
        mdc.echo_trace_id == mdc.server_trace_id
        mdc.echo_span_id != mdc.server_span_id
    }

    private Map<String, String> get(String path) {
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)
        try {
            return client.toBlocking().retrieve(HttpRequest.GET(path), Argument.mapOf(String, String))
        } finally {
            client.close()
        }
    }

    private InMemorySpanExporter exporter() {
        context.getBean(InMemorySpanExporter)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/mdc')
    static class MdcController {

        @Inject
        MdcService service

        @Inject
        @Client('/')
        HttpClient client

        static Map<String, String> snapshot() {
            Map<String, String> mdc = MDC.copyOfContextMap ?: [:]
            mdc.subMap(['trace_id', 'span_id', 'trace_flags'].findAll { mdc.containsKey(it) })
        }

        static Map<String, String> withCurrent(Map<String, String> mdc) {
            def spanContext = Span.current().spanContext
            mdc + [current_trace_id: spanContext.traceId, current_span_id: spanContext.spanId, current_trace_flags: spanContext.traceFlags.asHex()]
        }

        @Get('/sync')
        Map<String, String> sync() {
            withCurrent(snapshot())
        }

        @Get('/untraced')
        Map<String, String> untraced() {
            snapshot()
        }

        @Get('/echo')
        Map<String, String> echo() {
            snapshot()
        }

        @Get('/newSpan')
        Map<String, String> newSpan() {
            String serverSpanId = Span.current().spanContext.spanId
            Map<String, String> mdc = service.inSpan()
            mdc + [server_span_id: serverSpanId, after_span_id: MDC.get('span_id')]
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get('/blocking')
        Map<String, String> blocking() {
            withCurrent(snapshot())
        }

        @Get('/reactive')
        @SingleResult
        Publisher<Map<String, String>> reactive() {
            def server = Span.current().spanContext
            Mono.from(client.retrieve(HttpRequest.GET('/mdc/echo'), Argument.mapOf(String, String)))
                .map { Map<String, String> echo ->
                    snapshot() + [
                        server_trace_id: server.traceId,
                        server_span_id : server.spanId,
                        echo_trace_id  : echo.trace_id,
                        echo_span_id   : echo.span_id
                    ]
                }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class MdcService {

        @NewSpan
        Map<String, String> inSpan() {
            MdcController.withCurrent(MdcController.snapshot())
        }
    }
}
