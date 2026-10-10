/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.opentelemetry.instrument.websocket;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.micronaut.tracing.opentelemetry.annotation.WebSocketSpan;
import io.micronaut.tracing.opentelemetry.instrument.http.server.OpenTelemetryServerFilter;
import io.micronaut.tracing.util.TracedMethod;
import io.micronaut.tracing.util.TracedMethodCache;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.micronaut.websocket.annotation.WebSocketComponent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.semconv.CodeAttributes;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Traces the invocations of the {@code @OnOpen}, {@code @OnMessage}, {@code @OnClose} and {@code @OnError}
 * methods of the {@code @ServerWebSocket} and {@code @ClientWebSocket} classes. OpenTelemetry has no semantic
 * conventions for WebSocket, so each invocation is an {@code INTERNAL} span named after the event and the URI
 * template of the WebSocket, for example {@code MESSAGE /chat/{topic}}. The span has the
 * {@code code.function.name} attribute, the {@code micronaut.websocket.event} attribute ({@code open},
 * {@code message}, {@code pong}, {@code close} or {@code error}) and, when the method has a
 * {@link WebSocketSession} parameter, the {@code micronaut.websocket.session.id} attribute (an attribute only,
 * so that the span names keep a low cardinality).
 *
 * <p>On the server, the span of {@code @OnOpen} is a child of the span of the HTTP upgrade request. The
 * spans of the other handlers, which happen for as long as the session lives, are the root spans of new
 * traces, linked to the span of the upgrade request. On the client, the spans are root spans. The exception of
 * an {@code @OnError} method is recorded on its span; a handler that throws has the error status.</p>
 *
 * <p>The interceptor is bound by {@link WebSocketSpan}, which the {@code micronaut-tracing-opentelemetry-annotation}
 * processor adds to the WebSocket handler methods. Disabled with
 * {@code tracing.opentelemetry.websocket.enabled=false}.</p>
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@Internal
@Singleton
@Requires(beans = OpenTelemetry.class)
@Requires(classes = WebSocketSession.class)
@Requires(property = OpenTelemetryWebSocketConfig.ENABLED, notEquals = StringUtils.FALSE)
@InterceptorBean(WebSocketSpan.class)
public final class WebSocketSpanInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * The {@code micronaut.websocket.event} attribute: {@code open}, {@code message}, {@code pong},
     * {@code close} or {@code error}.
     */
    public static final AttributeKey<String> EVENT = AttributeKey.stringKey("micronaut.websocket.event");

    /**
     * The {@code micronaut.websocket.session.id} attribute: the id of the WebSocket session.
     */
    public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("micronaut.websocket.session.id");

    private static final String INSTRUMENTATION_NAME = "io.micronaut.websocket";

    private final Instrumenter<HandlerInvocation, Object> instrumenter;
    private final ConversionService conversionService;
    private final TracedMethodCache<HandlerMethod> methods = new TracedMethodCache<>(this::resolve);

    /**
     * @param openTelemetry     the OpenTelemetry instance
     * @param conversionService the conversion service
     */
    public WebSocketSpanInterceptor(OpenTelemetry openTelemetry, ConversionService conversionService) {
        this.conversionService = conversionService;
        this.instrumenter = Instrumenter.<HandlerInvocation, Object>builder(openTelemetry, INSTRUMENTATION_NAME,
                invocation -> invocation.method().spanName())
            .addAttributesExtractor(new HandlerAttributesExtractor())
            .addSpanLinksExtractor((links, parentContext, invocation) -> {
                if (invocation.link() != null) {
                    links.addLink(invocation.link());
                }
            })
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
    }

    @Override
    public int getOrder() {
        // outside of the other tracing interceptors of the method, such as @NewSpan
        return InterceptPhase.TRACE.getPosition() - 1;
    }

    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        HandlerMethod method = methods.get(context);
        if (method.event() == null) {
            return context.proceed();
        }
        Object[] parameters = context.getParameterValues();
        String sessionId = method.sessionIndex() >= 0 && parameters[method.sessionIndex()] instanceof WebSocketSession session
            ? session.getId()
            : null;
        Context upgradeContext = method.server() ? upgradeContext() : null;
        Context parentContext;
        SpanContext link = null;
        if (upgradeContext == null) {
            parentContext = Context.root();
        } else if (method.event() == Event.OPEN) {
            parentContext = upgradeContext;
        } else {
            // the session outlives the upgrade request: a new trace, linked to the upgrade request
            parentContext = Context.root();
            link = Span.fromContext(upgradeContext).getSpanContext();
        }
        HandlerInvocation invocation = new HandlerInvocation(method, sessionId, link);
        if (!instrumenter.shouldStart(parentContext, invocation)) {
            return context.proceed();
        }
        Context spanContext = instrumenter.start(parentContext, invocation);
        if (method.errorIndex() >= 0 && parameters[method.errorIndex()] instanceof Throwable error) {
            Span.fromContext(spanContext).recordException(error);
        }
        SpanEnd spanEnd = new SpanEnd(instrumenter, spanContext, invocation);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(spanContext))
            .propagate()) {
            if (method.synchronous()) {
                Object result = context.proceed();
                spanEnd.end(result, null);
                return result;
            }
            return interceptAsync(context, method, spanEnd);
        } catch (Throwable e) {
            spanEnd.accept(e);
            throw e;
        }
    }

    private @Nullable Object interceptAsync(MethodInvocationContext<Object, Object> context, HandlerMethod method, SpanEnd spanEnd) {
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    Publisher<?> publisher = interceptedMethod.interceptResultAsPublisher();
                    if (method.single()) {
                        return interceptedMethod.handleResult(Mono.from(publisher)
                            .doOnSuccess(spanEnd::success)
                            .doOnError(spanEnd)
                            .doOnCancel(spanEnd));
                    }
                    return interceptedMethod.handleResult(Flux.from(publisher)
                        .doOnComplete(spanEnd)
                        .doOnError(spanEnd)
                        .doOnCancel(spanEnd));
                }
                case COMPLETION_STAGE -> {
                    CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                    return interceptedMethod.handleResult(completionStage.whenComplete(spanEnd::end));
                }
                case SYNCHRONOUS -> {
                    Object result = context.proceed();
                    spanEnd.success(result);
                    return result;
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            spanEnd.accept(e);
            return interceptedMethod.handleException(e);
        }
    }

    /**
     * @return the context of the span of the HTTP upgrade request of the session, if traced
     */
    private static @Nullable Context upgradeContext() {
        return ServerRequestContext.currentRequest()
            .flatMap(request -> request.getAttribute(OpenTelemetryServerFilter.WEBSOCKET_UPGRADE_CONTEXT, Context.class))
            .orElse(null);
    }

    private HandlerMethod resolve(MethodInvocationContext<?, ?> context) {
        Event event = event(context);
        Class<?> declaringType = context.getDeclaringType();
        String functionName = declaringType.getName() + '.' + TracedMethod.methodName(context.getExecutableMethod());
        String uri = context.stringValue(WebSocketComponent.class).orElse(WebSocketComponent.DEFAULT_URI);
        String spanName = event == null ? functionName : event.name() + ' ' + uri;
        int sessionIndex = -1;
        int errorIndex = -1;
        Argument<?>[] arguments = context.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            Class<?> type = arguments[i].getType();
            if (sessionIndex < 0 && WebSocketSession.class.isAssignableFrom(type)) {
                sessionIndex = i;
            } else if (errorIndex < 0 && event == Event.ERROR && Throwable.class.isAssignableFrom(type)) {
                errorIndex = i;
            }
        }
        boolean synchronous = !context.isSuspend()
            && InterceptedMethod.of(context, conversionService).resultType() == InterceptedMethod.ResultType.SYNCHRONOUS;
        return new HandlerMethod(event, spanName, functionName, context.hasStereotype(ServerWebSocket.class),
            sessionIndex, errorIndex, synchronous, Publishers.isSingle(context.getReturnType().getType()));
    }

    private static @Nullable Event event(MethodInvocationContext<?, ?> context) {
        if (context.hasAnnotation(OnOpen.class)) {
            return Event.OPEN;
        }
        if (context.hasAnnotation(OnClose.class)) {
            return Event.CLOSE;
        }
        if (context.hasAnnotation(OnError.class)) {
            return Event.ERROR;
        }
        if (context.hasAnnotation(OnMessage.class)) {
            for (Class<?> type : context.getArgumentTypes()) {
                if (type == WebSocketPongMessage.class) {
                    return Event.PONG;
                }
            }
            return Event.MESSAGE;
        }
        return null;
    }

    /**
     * The WebSocket events.
     */
    enum Event {
        OPEN, MESSAGE, PONG, CLOSE, ERROR;

        private final String attribute = name().toLowerCase(Locale.ROOT);
    }

    /**
     * The per-method data of the interceptor.
     *
     * @param event        the event handled by the method, {@code null} if the method is not a handler
     * @param spanName     the span name
     * @param functionName the fully qualified name of the method, the {@code code.function.name} attribute
     * @param server       whether the method is a handler of a server WebSocket
     * @param sessionIndex the index of the {@link WebSocketSession} parameter, or {@code -1}
     * @param errorIndex   the index of the {@link Throwable} parameter of an {@code @OnError} method, or {@code -1}
     * @param synchronous  whether the method returns neither a reactive type nor a future
     * @param single       whether the reactive return type emits a single item
     */
    record HandlerMethod(@Nullable Event event,
                         String spanName,
                         String functionName,
                         boolean server,
                         int sessionIndex,
                         int errorIndex,
                         boolean synchronous,
                         boolean single) {
    }

    /**
     * An invocation of a handler, the request of the instrumenter.
     *
     * @param method    the handler method
     * @param sessionId the id of the session, if known
     * @param link      the span of the HTTP upgrade request to link to, if any
     */
    record HandlerInvocation(HandlerMethod method, @Nullable String sessionId, @Nullable SpanContext link) {
    }

    /**
     * Adds the code, event and session attributes.
     */
    private static final class HandlerAttributesExtractor implements AttributesExtractor<HandlerInvocation, Object> {

        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, HandlerInvocation invocation) {
            HandlerMethod method = invocation.method();
            attributes.put(CodeAttributes.CODE_FUNCTION_NAME, method.functionName());
            if (method.event() != null) {
                attributes.put(EVENT, method.event().attribute);
            }
            if (invocation.sessionId() != null) {
                attributes.put(SESSION_ID, invocation.sessionId());
            }
        }

        @Override
        public void onEnd(AttributesBuilder attributes,
                          Context context,
                          HandlerInvocation invocation,
                          @Nullable Object response,
                          @Nullable Throwable error) {
            // the attributes are only added on start
        }
    }

    /**
     * Ends the span once, whichever of the completion, error or cancellation signals comes first.
     */
    private static final class SpanEnd extends AtomicBoolean implements Runnable, Consumer<Throwable> {

        private final transient Instrumenter<HandlerInvocation, Object> instrumenter;
        private final transient Context context;
        private final transient HandlerInvocation invocation;

        SpanEnd(Instrumenter<HandlerInvocation, Object> instrumenter, Context context, HandlerInvocation invocation) {
            this.instrumenter = instrumenter;
            this.context = context;
            this.invocation = invocation;
        }

        void end(@Nullable Object response, @Nullable Throwable error) {
            if (compareAndSet(false, true)) {
                instrumenter.end(context, invocation, response, error);
            }
        }

        void success(@Nullable Object response) {
            end(response, null);
        }

        @Override
        public void run() {
            end(null, null);
        }

        @Override
        public void accept(Throwable throwable) {
            end(null, throwable);
        }
    }
}
