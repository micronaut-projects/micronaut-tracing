package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Envelope
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.rabbitmq.connect.ChannelPool
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import spock.lang.Specification

class TracingChannelPoolSpec extends Specification {

    void "channel wrapper traces consumer deliveries"() {
        given:
        def exporter = InMemorySpanExporter.create()
        OpenTelemetry openTelemetry = openTelemetry(exporter)
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

    void "channel pool delegates metadata and unwraps returned channels"() {
        given:
        def telemetry = new RabbitMQTelemetry(openTelemetry(InMemorySpanExporter.create()))
        def delegateChannel = Mock(Channel)
        def delegatePool = Mock(ChannelPool)
        def pool = new TracingChannelPool(delegatePool, telemetry)

        when:
        def name = pool.name
        def topologyRecovery = pool.topologyRecoveryEnabled
        def channel = pool.getChannelWithRecoveringDelay(3)
        pool.returnChannel(channel)

        then:
        name == "default"
        topologyRecovery
        channel instanceof RabbitMQTelemetry.TracingChannel
        1 * delegatePool.getName() >> "default"
        1 * delegatePool.isTopologyRecoveryEnabled() >> true
        1 * delegatePool.getChannelWithRecoveringDelay(3) >> delegateChannel
        1 * delegatePool.returnChannel(delegateChannel)
    }

    void "channel pool instrumentation preserves existing tracing wrapper"() {
        given:
        def telemetry = new RabbitMQTelemetry(openTelemetry(InMemorySpanExporter.create()))
        def delegatePool = Stub(ChannelPool)
        def pool = new TracingChannelPool(delegatePool, telemetry)
        def instrumentation = new RabbitMQChannelPoolTracingInstrumentation(telemetry)
        def event = Stub(BeanCreatedEvent) {
            getBean() >> pool
        }

        expect:
        instrumentation.onCreated(event).is(pool)
    }

    void "consumer wrapper delegates lifecycle callbacks"() {
        given:
        def telemetry = new RabbitMQTelemetry(openTelemetry(InMemorySpanExporter.create()))
        def delegate = Mock(Consumer)
        def consumer = new TracingConsumer(delegate, telemetry)

        when:
        consumer.handleConsumeOk("tag")
        consumer.handleCancelOk("tag")
        consumer.handleCancel("tag")
        consumer.handleShutdownSignal("tag", null)
        consumer.handleRecoverOk("tag")

        then:
        1 * delegate.handleConsumeOk("tag")
        1 * delegate.handleCancelOk("tag")
        1 * delegate.handleCancel("tag")
        1 * delegate.handleShutdownSignal("tag", null)
        1 * delegate.handleRecoverOk("tag")
    }

    void "delivery failures record span error"() {
        given:
        def exporter = InMemorySpanExporter.create()
        def telemetry = new RabbitMQTelemetry(openTelemetry(exporter))
        def delegate = Mock(Consumer)
        def consumer = new TracingConsumer(delegate, telemetry)
        def failure = new IOException("boom")

        when:
        consumer.handleDelivery("consumer", null, null, "body".bytes)

        then:
        1 * delegate.handleDelivery("consumer", null, null, "body".bytes) >> { throw failure }
        def e = thrown(IOException)
        e.is(failure)
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].status.statusCode.name() == "ERROR"
    }

    private static OpenTelemetry openTelemetry(InMemorySpanExporter exporter) {
        OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.instance))
                .build()
    }
}
