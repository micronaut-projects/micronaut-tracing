package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.LongString
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Property
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.rabbitmq.annotation.Binding
import io.micronaut.rabbitmq.annotation.Queue
import io.micronaut.rabbitmq.annotation.RabbitClient
import io.micronaut.rabbitmq.annotation.RabbitConnection
import io.micronaut.rabbitmq.annotation.RabbitListener
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.tracing.util.RabbitMQ
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

@MicronautTest
@Property(name = "spec.name", value = "RabbitMQTelemetryIntegrationSpec")
class RabbitMQTelemetryIntegrationSpec extends Specification implements TestPropertyProvider {

    @Inject ProductClient productClient
    @Inject ProductListener productListener
    @Inject ExecutorListener executorListener
    @Inject InMemorySpanExporter exporter

    @Override
    Map<String, String> getProperties() {
        def properties = RabbitMQ.getProperties()
        declareQueue(properties, "product")
        declareQueue(properties, "product-executor")
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

    void "listener running on an executor sees the delivery trace context"() {
        given:
        def conditions = new PollingConditions(timeout: 30)
        String message = "executor-${UUID.randomUUID()}"
        exporter.reset()
        executorListener.traceIds.clear()

        when:
        productClient.sendToExecutor(message.getBytes(StandardCharsets.UTF_8))

        then:
        conditions.eventually {
            def spans = exporter.finishedSpanItems.toList()
            def producer = spans.find { it.kind == SpanKind.PRODUCER }
            def consumer = spans.find { it.kind == SpanKind.CONSUMER }
            assert producer != null
            assert consumer != null
            assert executorListener.traceIds[message] != null
            assert executorListener.threads[message] != null
            assert executorListener.threads[message].startsWith("rabbit-tracing-executor")
            assert executorListener.traceIds[message] == producer.spanContext.traceId
            assert executorListener.spanIds[message] == consumer.spanContext.spanId
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

        @Binding("product-executor")
        void sendToExecutor(byte[] data)
    }

    @Requires(property = "spec.name", value = "RabbitMQTelemetryIntegrationSpec")
    @RabbitListener
    static class ExecutorListener {
        final Map<String, String> traceIds = new ConcurrentHashMap<>()
        final Map<String, String> spanIds = new ConcurrentHashMap<>()
        final Map<String, String> threads = new ConcurrentHashMap<>()

        @Queue("product-executor")
        @RabbitConnection(executor = "rabbit-tracing")
        void receive(byte[] data) {
            String message = new String(data, StandardCharsets.UTF_8)
            def spanContext = Span.current().spanContext
            spanIds.put(message, spanContext.spanId)
            threads.put(message, Thread.currentThread().name)
            traceIds.put(message, spanContext.traceId)
        }
    }

    /**
     * Executor that propagates the Micronaut {@link PropagatedContext} to submitted tasks.
     */
    @Requires(property = "spec.name", value = "RabbitMQTelemetryIntegrationSpec")
    @Factory
    static class PropagatingExecutorFactory {

        @Singleton
        @Named("rabbit-tracing")
        @Bean(preDestroy = "shutdown")
        ExecutorService rabbitTracingExecutor() {
            def delegate = Executors.newFixedThreadPool(2, { Runnable r -> new Thread(r, "rabbit-tracing-executor") } as ThreadFactory)
            return new AbstractExecutorService() {
                @Override
                void execute(Runnable command) {
                    delegate.execute(PropagatedContext.wrapCurrent(command))
                }

                @Override
                void shutdown() {
                    delegate.shutdown()
                }

                @Override
                List<Runnable> shutdownNow() {
                    delegate.shutdownNow()
                }

                @Override
                boolean isShutdown() {
                    delegate.isShutdown()
                }

                @Override
                boolean isTerminated() {
                    delegate.isTerminated()
                }

                @Override
                boolean awaitTermination(long timeout, TimeUnit unit) {
                    delegate.awaitTermination(timeout, unit)
                }
            }
        }
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
