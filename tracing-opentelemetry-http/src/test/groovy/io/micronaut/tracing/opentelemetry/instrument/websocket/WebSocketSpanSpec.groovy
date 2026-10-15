package io.micronaut.tracing.opentelemetry.instrument.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.opentelemetry.annotation.WebSocketSpan
import io.micronaut.tracing.opentelemetry.test.TestSpans
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketPongMessage
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.CodeAttributes
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.api.trace.Span
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebSocketSpanSpec extends Specification {

    private static final String SPEC_NAME = 'WebSocketSpanSpec'
    private static final String SERVER = EchoServer.name
    private static final String CLIENT = EchoClient.name

    @AutoCleanup
    ApplicationContext context

    @AutoCleanup
    WebSocketClient webSocketClient

    TestSpans spans

    private void start(Map<String, Object> properties = [:]) {
        context = ApplicationContext.run([
            'spec.name'           : SPEC_NAME,
            'otel.register.global': false
        ] + properties)
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        webSocketClient = context.createBean(WebSocketClient, server.URI)
        spans = context.getBean(TestSpans)
    }

    private EchoClient connect() {
        Flux.from(webSocketClient.connect(EchoClient, '/echo/lobby')).blockFirst()
    }

    private SpanData span(String name, String declaringType) {
        new PollingConditions(timeout: 10).eventually {
            assert matching(name, declaringType).size() == 1
        }
        matching(name, declaringType)[0]
    }

    private List<SpanData> matching(String name, String declaringType) {
        spans.finishedSpans().findAll {
            it.name == name && it.attributes.get(CodeAttributes.CODE_FUNCTION_NAME)?.startsWith(declaringType + '.')
        }
    }

    void 'the WebSocket handlers of the server and the client are traced'() {
        given:
        start()

        when:
        EchoClient client = connect()
        client.send('hello')

        then:
        client.replies.poll(10, TimeUnit.SECONDS) == 'hello'

        when:
        client.close()
        new PollingConditions(timeout: 10).eventually {
            assert spans.spansOfKind(SpanKind.SERVER).size() == 1
        }
        SpanData upgrade = spans.spansOfKind(SpanKind.SERVER)[0]
        SpanData open = span('OPEN /echo/{room}', SERVER)
        SpanData message = span('MESSAGE /echo/{room}', SERVER)
        SpanData close = span('CLOSE /echo/{room}', SERVER)
        SpanData clientMessage = span('MESSAGE /echo/{room}', CLIENT)

        then: 'the open span is a child of the upgrade request span'
        context.getBean(OpenTelemetryWebSocketConfig).enabled
        open.kind == SpanKind.INTERNAL
        open.traceId == upgrade.traceId
        open.parentSpanId == upgrade.spanId
        open.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == SERVER + '.onOpen'
        open.attributes.get(WebSocketSpanInterceptor.EVENT) == 'open'
        open.instrumentationScopeInfo.name == 'io.micronaut.websocket'

        and: 'the other handlers start new traces, linked to the upgrade request span'
        [message, close].every { !it.parentSpanContext.valid }
        [message, close].every { it.links*.spanContext*.spanId == [upgrade.spanId] }
        message.attributes.get(WebSocketSpanInterceptor.EVENT) == 'message'
        close.attributes.get(WebSocketSpanInterceptor.EVENT) == 'close'
        message.status.statusCode == StatusCode.UNSET

        and: 'the session id is an attribute of the spans of the server session'
        String sessionId = open.attributes.get(WebSocketSpanInterceptor.SESSION_ID)
        sessionId
        message.attributes.get(WebSocketSpanInterceptor.SESSION_ID) == sessionId
        close.attributes.get(WebSocketSpanInterceptor.SESSION_ID) == sessionId

        and: 'the client handler span is a root span, without the session parameter'
        !clientMessage.parentSpanContext.valid
        clientMessage.links.isEmpty()
        clientMessage.kind == SpanKind.INTERNAL
        clientMessage.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == CLIENT + '.onMessage'
        clientMessage.attributes.get(WebSocketSpanInterceptor.SESSION_ID) == null
    }

    void 'a failing handler has the error status and the error handler records the exception'() {
        given:
        start()

        when:
        EchoClient client = connect()
        client.send('fail')

        then:
        SpanData message = span('MESSAGE /echo/{room}', SERVER)
        message.status.statusCode == StatusCode.ERROR
        message.events.any { it.name == 'exception' && it.attributes.get(ExceptionAttributes.EXCEPTION_MESSAGE) == 'boom' }

        SpanData error = span('ERROR /echo/{room}', SERVER)
        error.attributes.get(WebSocketSpanInterceptor.EVENT) == 'error'
        error.events.any { it.name == 'exception' && it.attributes.get(ExceptionAttributes.EXCEPTION_MESSAGE) == 'boom' }
        error.status.statusCode == StatusCode.UNSET

        cleanup:
        client?.close()
    }

    void 'the publisher of a reactive handler is subscribed with the span of the handler'() {
        given:
        start()
        ReactiveServer server = context.getBean(ReactiveServer)

        when:
        EchoClient client = Flux.from(webSocketClient.connect(EchoClient, '/reactive/lobby')).blockFirst()
        client.send('hello')

        then:
        client.replies.poll(10, TimeUnit.SECONDS) == 'hello'

        when:
        SpanData message = span('MESSAGE /reactive/{room}', ReactiveServer.name)
        client.close()

        then: 'the deferred body ran with the span of the handler current'
        server.subscribedSpanId == message.spanId
    }

    void 'the WebSocket handlers are not traced when disabled'() {
        given:
        start('tracing.opentelemetry.websocket.enabled': false)

        when:
        EchoClient client = connect()
        client.send('hello')

        then:
        !context.containsBean(WebSocketSpanInterceptor)
        !context.getBean(OpenTelemetryWebSocketConfig).enabled
        client.replies.poll(10, TimeUnit.SECONDS) == 'hello'

        when:
        client.close()

        then:
        new PollingConditions(timeout: 10).eventually {
            assert context.getBean(EchoServer).closed
        }
        spans.finishedSpans().every { it.instrumentationScopeInfo.name != 'io.micronaut.websocket' }
        spans.spansOfKind(SpanKind.SERVER).size() == 1
    }

    void 'the Flux and CompletionStage results of the handlers end the spans when they complete'() {
        given:
        start()

        when:
        EchoClient fluxClient = Flux.from(webSocketClient.connect(EchoClient, '/flux/lobby')).blockFirst()
        fluxClient.send('hello')
        EchoClient futureClient = Flux.from(webSocketClient.connect(EchoClient, '/future/lobby')).blockFirst()
        futureClient.send('hello')

        then:
        fluxClient.replies.poll(10, TimeUnit.SECONDS) == 'hello'
        futureClient.replies.poll(10, TimeUnit.SECONDS) == 'hello'

        when:
        SpanData flux = span('MESSAGE /flux/{room}', FluxServer.name)
        SpanData future = span('MESSAGE /future/{room}', FutureServer.name)

        then:
        flux.status.statusCode == StatusCode.UNSET
        flux.attributes.get(WebSocketSpanInterceptor.EVENT) == 'message'
        flux.attributes.get(WebSocketSpanInterceptor.SESSION_ID)
        future.status.statusCode == StatusCode.UNSET
        future.attributes.get(WebSocketSpanInterceptor.EVENT) == 'message'

        cleanup:
        fluxClient?.close()
        futureClient?.close()
    }

    void 'a reactive handler that throws or fails has the error status'() {
        given:
        start()

        when:
        EchoClient fluxClient = Flux.from(webSocketClient.connect(EchoClient, '/flux/lobby')).blockFirst()
        fluxClient.send('fail')
        EchoClient futureClient = Flux.from(webSocketClient.connect(EchoClient, '/future/lobby')).blockFirst()
        futureClient.send('fail')

        then: 'the handler returning a Flux threw before returning it'
        SpanData flux = span('MESSAGE /flux/{room}', FluxServer.name)
        flux.status.statusCode == StatusCode.ERROR
        flux.events.any { it.name == 'exception' && it.attributes.get(ExceptionAttributes.EXCEPTION_MESSAGE) == 'flux boom' }

        and: 'the CompletionStage returned by the handler failed'
        SpanData future = span('MESSAGE /future/{room}', FutureServer.name)
        future.status.statusCode == StatusCode.ERROR
        future.events.any { it.name == 'exception' && it.attributes.get(ExceptionAttributes.EXCEPTION_MESSAGE) == 'future boom' }

        cleanup:
        fluxClient?.close()
        futureClient?.close()
    }

    void 'a pong handler is traced with the pong event'() {
        given:
        start()

        when: 'the server pings the client on open, the client replies with a pong'
        EchoClient client = Flux.from(webSocketClient.connect(EchoClient, '/pong/lobby')).blockFirst()
        SpanData pong = span('PONG /pong/{room}', PongServer.name)

        then:
        pong.attributes.get(WebSocketSpanInterceptor.EVENT) == 'pong'
        pong.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == PongServer.name + '.onPong'
        pong.attributes.get(WebSocketSpanInterceptor.SESSION_ID)
        context.getBean(PongServer).pongs.get() >= 1

        cleanup:
        client?.close()
    }

    void 'the handlers of a server session whose upgrade request is not traced are root spans'() {
        given:
        start('otel.exclusions[0]': '/echo/.*')

        when:
        EchoClient client = connect()
        client.send('hello')

        then:
        client.replies.poll(10, TimeUnit.SECONDS) == 'hello'

        when:
        SpanData open = span('OPEN /echo/{room}', SERVER)
        SpanData message = span('MESSAGE /echo/{room}', SERVER)

        then:
        spans.spansOfKind(SpanKind.SERVER).isEmpty()
        !open.parentSpanContext.valid
        open.links.isEmpty()
        !message.parentSpanContext.valid
        message.links.isEmpty()

        cleanup:
        client?.close()
    }

    void 'a method bound to the interceptor that is not a WebSocket handler is not traced'() {
        given:
        start()

        when:
        String result = context.getBean(NotAHandler).call()

        then:
        result == 'called'
        spans.finishedSpans().every { it.instrumentationScopeInfo.name != 'io.micronaut.websocket' }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @ServerWebSocket('/echo/{room}')
    static class EchoServer {

        volatile boolean closed

        @OnOpen
        void onOpen(String room, WebSocketSession session) {
        }

        @OnMessage
        void onMessage(String room, String message, WebSocketSession session) {
            if (message == 'fail') {
                throw new IllegalStateException('boom')
            }
            session.sendSync(message)
        }

        @OnClose
        void onClose(String room, WebSocketSession session) {
            closed = true
        }

        @OnError
        void onError(String room, WebSocketSession session, Throwable error) {
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @ServerWebSocket('/reactive/{room}')
    static class ReactiveServer {

        volatile String subscribedSpanId

        @OnMessage
        Mono<String> onMessage(String room, String message, WebSocketSession session) {
            Mono.defer {
                subscribedSpanId = Span.current().spanContext.spanId
                Mono.from(session.send(message))
            }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @ServerWebSocket('/flux/{room}')
    static class FluxServer {

        @OnMessage
        Flux<String> onMessage(String room, String message, WebSocketSession session) {
            if (message == 'fail') {
                throw new IllegalStateException('flux boom')
            }
            Flux.from(session.send(message))
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @ServerWebSocket('/future/{room}')
    static class FutureServer {

        @OnMessage
        CompletionStage<String> onMessage(String room, String message, WebSocketSession session) {
            if (message == 'fail') {
                return CompletableFuture.failedFuture(new IllegalStateException('future boom'))
            }
            session.sendAsync(message)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @ServerWebSocket('/pong/{room}')
    static class PongServer {

        final AtomicInteger pongs = new AtomicInteger()

        @OnOpen
        void onOpen(String room, WebSocketSession session) {
            session.sendPingAsync('ping'.bytes)
        }

        @OnMessage
        void onMessage(String room, String message, WebSocketSession session) {
            session.sendSync(message)
        }

        @OnMessage
        void onPong(WebSocketPongMessage pong, WebSocketSession session) {
            pongs.incrementAndGet()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class NotAHandler {

        @WebSocketSpan
        String call() {
            'called'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @ClientWebSocket('/echo/{room}')
    static abstract class EchoClient implements AutoCloseable {

        final BlockingQueue<String> replies = new LinkedBlockingQueue<>()

        @OnMessage
        void onMessage(String message) {
            replies.add(message)
        }

        abstract void send(String message)
    }
}
