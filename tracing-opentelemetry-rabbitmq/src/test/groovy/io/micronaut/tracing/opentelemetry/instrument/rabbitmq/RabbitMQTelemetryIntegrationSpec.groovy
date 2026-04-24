package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

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

@MicronautTest
@Property(name = "spec.name", value = "RabbitMQTelemetryIntegrationSpec")
class RabbitMQTelemetryIntegrationSpec extends Specification implements TestPropertyProvider {

    @Inject ProductClient productClient
    @Inject ProductListener productListener
    @Inject InMemorySpanExporter exporter

    @Override
    Map<String, String> getProperties() {
        return RabbitMQ.getProperties() + [
                'otel.register.global': 'false',
                'micronaut.application.name': 'rabbitmq-test'
        ]
    }

    void "rabbitmq publish and consume creates spans"() {
        given:
        def conditions = new PollingConditions(timeout: 30)

        when:
        productClient.send("quickstart".bytes)

        then:
        conditions.eventually {
            productListener.messages == ["quickstart"]
            exporter.finishedSpanItems.count { it.kind == SpanKind.PRODUCER } == 1
            exporter.finishedSpanItems.count { it.kind == SpanKind.CONSUMER } == 1
        }
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
            messages.add(new String(data))
        }
    }
}
