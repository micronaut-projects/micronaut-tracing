package io.micronaut.tracing.opentelemetry.instrument.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.opentelemetry.test.TestSpans
import io.micronaut.websocket.WebSocketClient
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
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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

    void 'the WebSocket handlers are not traced when disabled'() {
        given:
        start('tracing.opentelemetry.websocket.enabled': false)

        when:
        EchoClient client = connect()
        client.send('hello')

        then:
        !context.containsBean(WebSocketSpanInterceptor)
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
