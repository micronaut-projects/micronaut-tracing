package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.LongString
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Property
import io.micronaut.rabbitmq.annotation.Binding
import io.micronaut.rabbitmq.annotation.Queue
import io.micronaut.rabbitmq.annotation.RabbitClient
import io.micronaut.rabbitmq.annotation.RabbitListener
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.tracing.util.RabbitMQ
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

@MicronautTest
@Property(name = "spec.name", value = "RabbitMQTelemetryIntegrationSpec")
class RabbitMQTelemetryIntegrationSpec extends Specification implements TestPropertyProvider {

    @Inject ProductClient productClient
    @Inject ProductListener productListener
    @Inject InMemorySpanExporter exporter

    @Override
    Map<String, String> getProperties() {
        def properties = RabbitMQ.getProperties()
        declareQueue(properties, "product")
        return properties + [
                'otel.register.global': 'false',
                'micronaut.application.name': 'rabbitmq-test'
        ]
    }

    void "rabbitmq publish and consume creates spans"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        String message = "quickstart-${UUID.randomUUID()}"
        exporter.reset()
        productListener.messages.clear()

        when:
        productClient.send(message.getBytes(StandardCharsets.UTF_8))

        then:
        conditions.eventually {
            def spans = exporter.finishedSpanItems.toList()
            def producer = spans.find { it.kind == SpanKind.PRODUCER }
            def consumer = spans.find { it.kind == SpanKind.CONSUMER }
            assert productListener.messages.contains(message)
            assert spans.count { it.kind == SpanKind.PRODUCER } == 1
            assert spans.count { it.kind == SpanKind.CONSUMER } == 1
            assert producer != null
            assert consumer != null
            assert consumer.spanContext.traceId == producer.spanContext.traceId
            assert consumer.parentSpanContext.spanId == producer.spanContext.spanId
            assert consumer.parentSpanContext.remote
            assert producer.attributes.get(RabbitMQTelemetry.MESSAGING_SYSTEM) == "rabbitmq"
            assert producer.attributes.get(RabbitMQTelemetry.MESSAGING_OPERATION) == "publish"
            assert producer.attributes.get(RabbitMQTelemetry.MESSAGING_OPERATION_NAME) == "publish"
            assert producer.attributes.get(RabbitMQTelemetry.MESSAGING_OPERATION_TYPE) == "send"
            assert producer.attributes.get(RabbitMQTelemetry.DESTINATION) == "product"
            assert consumer.attributes.get(RabbitMQTelemetry.MESSAGING_SYSTEM) == "rabbitmq"
            assert consumer.attributes.get(RabbitMQTelemetry.MESSAGING_OPERATION) == "process"
            assert consumer.attributes.get(RabbitMQTelemetry.MESSAGING_OPERATION_NAME) == "process"
            assert consumer.attributes.get(RabbitMQTelemetry.MESSAGING_OPERATION_TYPE) == "process"
            assert consumer.attributes.get(RabbitMQTelemetry.ROUTING_KEY) == "product"
            assert consumer.attributes.get(RabbitMQTelemetry.DELIVERY_TAG) > 0
        }
    }

    void "broker round trip exposes string headers as decodable AMQP values"() {
        given:
        def conditions = new PollingConditions(timeout: 10)
        def queue = "trace-headers-${UUID.randomUUID()}"
        def traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        def factory = connectionFactory()
        def connection = factory.newConnection()
        def channel = connection.createChannel()
        Map<String, Object> deliveredHeaders = null

        when:
        channel.queueDeclare(queue, false, true, true, null)
        channel.basicPublish("", queue, new AMQP.BasicProperties.Builder().headers([traceparent: traceparent]).build(), "body".getBytes(StandardCharsets.UTF_8))

        then:
        conditions.eventually {
            def response = channel.basicGet(queue, true)
            assert response != null
            deliveredHeaders = response.props.headers
            assert deliveredHeaders.traceparent instanceof LongString || deliveredHeaders.traceparent instanceof String
            assert new RabbitMQHeadersGetter().get(deliveredHeaders, "traceparent") == traceparent
        }

        cleanup:
        channel?.queueDelete(queue)
        channel?.close()
        connection?.close()
    }

    void "enabled false removes RabbitMQ telemetry beans"() {
        when:
        def context = ApplicationContext.run([
                'otel.instrumentation.rabbitmq.enabled': 'false',
                'otel.register.global': 'false'
        ])

        then:
        !context.containsBean(RabbitMQTelemetryConfiguration)
        !context.containsBean(RabbitMQTelemetry)
        !context.containsBean(RabbitMQReactivePublisherTracingInstrumentation)
        !context.containsBean(RabbitMQChannelPoolTracingInstrumentation)

        cleanup:
        context.close()
    }

    void "wrapper false keeps telemetry bean without wrapper instrumentation"() {
        when:
        def context = ApplicationContext.run([
                'otel.instrumentation.rabbitmq.wrapper': 'false',
                'otel.register.global': 'false'
        ])

        then:
        context.containsBean(RabbitMQTelemetryConfiguration)
        context.containsBean(RabbitMQTelemetry)
        !context.containsBean(RabbitMQReactivePublisherTracingInstrumentation)
        !context.containsBean(RabbitMQChannelPoolTracingInstrumentation)

        cleanup:
        context.close()
    }

    @Requires(property = "spec.name", value = "RabbitMQTelemetryIntegrationSpec")
    @RabbitClient
    static interface ProductClient {
        @Binding("product")
        void send(byte[] data)
    }

    @Requires(property = "spec.name", value = "RabbitMQTelemetryIntegrationSpec")
    @RabbitListener
    static class ProductListener {
        final List<String> messages = Collections.synchronizedList(new ArrayList<>())

        @Queue("product")
        void receive(byte[] data) {
            messages.add(new String(data, StandardCharsets.UTF_8))
        }
    }

    private static ConnectionFactory connectionFactory() {
        connectionFactory(RabbitMQ.getProperties())
    }

    private static ConnectionFactory connectionFactory(Map<String, String> properties) {
        def factory = new ConnectionFactory()
        factory.setUri(properties["rabbitmq.uri"])
        factory
    }

    private static void declareQueue(Map<String, String> properties, String queue) {
        def connection = connectionFactory(properties).newConnection()
        def channel = connection.createChannel()
        try {
            channel.queueDeclare(queue, false, false, false, null)
        } finally {
            channel.close()
            connection.close()
        }
    }
}
