package io.micronaut.tracing.opentelemetry.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.tracing.opentelemetry.annotation.WebSocketSpan

class WebSocketSpanVisitorSpec extends AbstractTypeElementSpec {

    void 'the visitor is isolating and supports the WebSocket handler annotations'() {
        given:
        def visitor = new WebSocketSpanVisitor()

        expect:
        visitor.visitorKind == TypeElementVisitor.VisitorKind.ISOLATING
        visitor.supportedAnnotationNames == [
            'io.micronaut.websocket.annotation.OnOpen',
            'io.micronaut.websocket.annotation.OnMessage',
            'io.micronaut.websocket.annotation.OnClose',
            'io.micronaut.websocket.annotation.OnError'
        ] as Set
    }

    void 'the handlers of a server WebSocket that can be intercepted are traced'() {
        given:
        def definition = buildBeanDefinition('test.Chat', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/chat/{topic}")
class Chat {

    @OnOpen
    public void onOpen(String topic, WebSocketSession session) {
    }

    @OnMessage
    public void onMessage(String topic, String message, WebSocketSession session) {
    }

    @OnClose
    public void onClose(String topic, WebSocketSession session) {
    }

    @OnError
    public void onError(String topic, WebSocketSession session, Throwable error) {
    }

    @Executable
    public void notAHandler() {
    }
}
''')

        expect:
        ['onOpen', 'onMessage', 'onClose', 'onError'].every { name ->
            definition.executableMethods.find { it.methodName == name }.hasAnnotation(WebSocketSpan)
        }
        !definition.findMethod('notAHandler').get().hasAnnotation(WebSocketSpan)
    }

    void 'the final handlers and the handlers of a final class are not traced'() {
        given:
        def definition = buildBeanDefinition('test.Chat', '''
package test;

import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/chat")
class Chat {

    @OnOpen
    public void onOpen() {
    }

    @OnMessage
    public final void onMessage(String message) {
    }
}
''')
        def finalDefinition = buildBeanDefinition('test.FinalChat', '''
package test;

import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/final")
final class FinalChat {

    @OnMessage
    public void onMessage(String message) {
    }
}
''')

        expect:
        definition.executableMethods.find { it.methodName == 'onOpen' }.hasAnnotation(WebSocketSpan)
        !definition.executableMethods.find { it.methodName == 'onMessage' }.hasAnnotation(WebSocketSpan)
        !finalDefinition.executableMethods.find { it.methodName == 'onMessage' }.hasAnnotation(WebSocketSpan)
    }

    void 'the handler annotations outside of a WebSocket class are not traced'() {
        given:
        def definition = buildBeanDefinition('test.NotAWebSocket', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.websocket.annotation.OnMessage;
import jakarta.inject.Singleton;

@Singleton
class NotAWebSocket {

    @OnMessage
    @Executable
    public void onMessage(String message) {
    }
}
''')

        expect:
        !definition.findMethod('onMessage', String).get().hasAnnotation(WebSocketSpan)
    }
}
