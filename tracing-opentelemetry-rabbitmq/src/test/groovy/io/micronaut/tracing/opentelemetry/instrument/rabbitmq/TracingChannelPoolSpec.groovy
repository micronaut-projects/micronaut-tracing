package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Envelope
import io.micronaut.rabbitmq.connect.ChannelPool
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import spock.lang.Specification

class TracingChannelPoolSpec extends Specification {

    void "channel wrapper traces consumer deliveries"() {
        given:
        def exporter = InMemorySpanExporter.create()
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.instance))
                .build()
        def telemetry = new RabbitMQTelemetry(openTelemetry)
        def delegateChannel = Mock(Channel)
        def delegatePool = Stub(ChannelPool) {
            getName() >> "default"
            getChannel() >> delegateChannel
            isTopologyRecoveryEnabled() >> true
        }
        def pool = new TracingChannelPool(delegatePool, telemetry)
        def consumer = Mock(Consumer)

        when:
        def channel = pool.getChannel()
        channel.basicConsume("orders", true, consumer)

        then:
        1 * delegateChannel.basicConsume("orders", true, _ as Consumer) >> { String queue, boolean autoAck, Consumer wrapped ->
            wrapped.handleDelivery("consumer", new Envelope(1L, false, "orders-exchange", "orders"), new AMQP.BasicProperties.Builder().headers([traceparent: "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"]).build(), "body".bytes)
            "consumer"
        }
        1 * consumer.handleDelivery("consumer", _ as Envelope, _ as AMQP.BasicProperties, "body".bytes)
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].kind.name() == "CONSUMER"
    }
}
