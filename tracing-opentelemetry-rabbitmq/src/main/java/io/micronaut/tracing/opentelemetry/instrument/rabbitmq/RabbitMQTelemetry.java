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
package io.micronaut.tracing.opentelemetry.instrument.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import io.micronaut.core.annotation.Internal;
import io.micronaut.rabbitmq.bind.RabbitConsumerState;
import io.micronaut.rabbitmq.connect.ChannelPool;
import io.micronaut.rabbitmq.reactive.RabbitPublishState;
import io.micronaut.rabbitmq.reactive.ReactivePublisher;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * RabbitMQ telemetry support based on bean wrappers.
 *
 * @since 8.0.0
 */
@Internal
public final class RabbitMQTelemetry {

    static final AttributeKey<String> MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system");
    static final AttributeKey<String> MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation");
    static final AttributeKey<String> MESSAGING_OPERATION_NAME = AttributeKey.stringKey("messaging.operation.name");
    static final AttributeKey<String> MESSAGING_OPERATION_TYPE = AttributeKey.stringKey("messaging.operation.type");
    static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    static final AttributeKey<String> EXCHANGE = AttributeKey.stringKey("messaging.rabbitmq.destination.exchange");
    static final AttributeKey<String> ROUTING_KEY = AttributeKey.stringKey("messaging.rabbitmq.destination.routing_key");
    static final AttributeKey<Long> DELIVERY_TAG = AttributeKey.longKey("messaging.rabbitmq.message.delivery_tag");
    static final AttributeKey<String> DESTINATION = AttributeKey.stringKey("messaging.destination.name");

    private static final String INSTRUMENTATION_NAME = "io.micronaut.tracing.rabbitmq";

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    RabbitMQTelemetry(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    ReactivePublisher wrap(ReactivePublisher reactivePublisher) {
        return new TracingReactivePublisher(reactivePublisher, this);
    }

    ChannelPool wrap(ChannelPool channelPool) {
        return new TracingChannelPool(channelPool, this);
    }

    Channel wrap(Channel channel) {
        if (channel instanceof TracingChannel) {
            return channel;
        }
        return (Channel) Proxy.newProxyInstance(
            Channel.class.getClassLoader(),
            new Class<?>[] {Channel.class, TracingChannel.class},
            new TracingChannelInvocationHandler(channel, this)
        );
    }

    Channel unwrap(Channel channel) {
        if (channel instanceof TracingChannel tracingChannel) {
            return tracingChannel.getDelegate();
        }
        return channel;
    }

    Consumer wrap(Consumer consumer) {
        if (consumer instanceof TracingConsumer) {
            return consumer;
        }
        return new TracingConsumer(consumer, this);
    }

    Publisher<Void> tracePublish(RabbitPublishState publishState, Function<RabbitPublishState, Publisher<Void>> publisherFactory) {
        return tracePublisherOperation("publish", publishState, publisherFactory);
    }

    Publisher<RabbitConsumerState> tracePublishAndReply(RabbitPublishState publishState, Function<RabbitPublishState, Publisher<RabbitConsumerState>> publisherFactory) {
        return tracePublisherOperation("publish", publishState, publisherFactory);
    }

    void handleDelivery(Consumer consumer, String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        Context parentContext = propagator.extract(Context.root(), properties == null ? null : properties.getHeaders(), new RabbitMQHeadersGetter());
        Span span = tracer.spanBuilder("rabbitmq process")
            .setParent(parentContext)
            .setSpanKind(SpanKind.CONSUMER)
            .startSpan();
        setSpanAttributes(span, "process", envelope == null ? "" : envelope.getExchange(), envelope == null ? "" : envelope.getRoutingKey());
        if (envelope != null) {
            span.setAttribute(DELIVERY_TAG, envelope.getDeliveryTag());
        }
        try (Scope ignored = parentContext.with(span).makeCurrent()) {
            consumer.handleDelivery(consumerTag, envelope, properties, body);
        } catch (IOException | RuntimeException e) {
            markFailed(span, e);
            throw e;
        } finally {
            span.end();
        }
    }

    private <T> Publisher<T> tracePublisherOperation(String operation, RabbitPublishState publishState, Function<RabbitPublishState, Publisher<T>> publisherFactory) {
        return Mono.defer(() -> {
            Context parentContext = Context.current();
            Span span = tracer.spanBuilder("rabbitmq " + operation)
                .setParent(parentContext)
                .setSpanKind(SpanKind.PRODUCER)
                .startSpan();
            setSpanAttributes(span, operation, publishState.getExchange(), publishState.getRoutingKey());
            Context context = parentContext.with(span);
            RabbitPublishState tracedState = injectContext(context, publishState);
            try {
                Publisher<T> publisher;
                try (Scope ignored = context.makeCurrent()) {
                    publisher = publisherFactory.apply(tracedState);
                }
                return Mono.from(publisher)
                    .doOnError(error -> markFailed(span, error))
                    .doFinally(signalType -> span.end());
            } catch (RuntimeException e) {
                markFailed(span, e);
                span.end();
                throw e;
            }
        });
    }

    private RabbitPublishState injectContext(Context context, RabbitPublishState publishState) {
        Map<String, Object> headers = publishState.getProperties().getHeaders() == null
            ? new HashMap<>()
            : new HashMap<>(publishState.getProperties().getHeaders());
        propagator.inject(context, headers, new RabbitMQHeadersSetter());
        AMQP.BasicProperties properties = publishState.getProperties().builder()
            .headers(headers)
            .build();
        return new RabbitPublishState(
            publishState.getExchange(),
            publishState.getRoutingKey(),
            publishState.getMandatory(),
            properties,
            publishState.getBody()
        );
    }

    private static void setSpanAttributes(Span span, String operation, String exchange, String routingKey) {
        span.setAttribute(MESSAGING_SYSTEM, "rabbitmq");
        span.setAttribute(MESSAGING_OPERATION, operation);
        span.setAttribute(MESSAGING_OPERATION_NAME, operation);
        span.setAttribute(MESSAGING_OPERATION_TYPE, operationType(operation));
        String destination = destination(exchange, routingKey);
        if (!destination.isEmpty()) {
            span.setAttribute(DESTINATION, destination);
        }
        if (exchange != null && !exchange.isEmpty()) {
            span.setAttribute(EXCHANGE, exchange);
        }
        if (routingKey != null && !routingKey.isEmpty()) {
            span.setAttribute(ROUTING_KEY, routingKey);
        }
    }

    private static void markFailed(Span span, Throwable error) {
        span.recordException(error);
        span.setAttribute(ERROR_TYPE, error.getClass().getName());
        span.setStatus(StatusCode.ERROR);
    }

    private static String operationType(String operation) {
        return "publish".equals(operation) ? "send" : operation;
    }

    private static String destination(String exchange, String routingKey) {
        if (exchange != null && !exchange.isEmpty() && routingKey != null && !routingKey.isEmpty()) {
            return exchange + ":" + routingKey;
        }
        if (exchange != null && !exchange.isEmpty()) {
            return exchange;
        }
        if (routingKey != null && !routingKey.isEmpty()) {
            return routingKey;
        }
        return "amq.default";
    }

    interface TracingChannel {
        Channel getDelegate();
    }

    private static final class TracingChannelInvocationHandler implements InvocationHandler {

        private final Channel channel;
        private final RabbitMQTelemetry telemetry;

        private TracingChannelInvocationHandler(Channel channel, RabbitMQTelemetry telemetry) {
            this.channel = channel;
            this.telemetry = telemetry;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == TracingChannel.class) {
                return channel;
            }
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "TracingChannel(" + channel + ")";
                    default -> method.invoke(channel, args);
                };
            }
            Object[] instrumentedArgs = instrumentArgs(method, args);
            try {
                return method.invoke(channel, instrumentedArgs);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Object[] instrumentArgs(Method method, Object[] args) {
            if (args == null || !"basicConsume".equals(method.getName())) {
                return args;
            }
            Object[] instrumentedArgs = args.clone();
            for (int i = 0; i < instrumentedArgs.length; i++) {
                if (instrumentedArgs[i] instanceof Consumer consumer) {
                    instrumentedArgs[i] = telemetry.wrap(consumer);
                }
            }
            return instrumentedArgs;
        }
    }
}
