package io.micronaut.tracing.opentelemetry.instrument.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.tracing.util.KafkaSetup
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import org.testcontainers.kafka.KafkaContainer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@MicronautTest
class KafkaTelemetryIntegrationSpec extends Specification implements TestPropertyProvider {

    @Inject TestKafkaClient testKafkaClient
    @Inject TestKafkaListener kafkaListener
    @Inject InMemorySpanExporter exporter

    @Override
    Map<String, String> getProperties() {
        // This triggers the container start and topic creation
        return KafkaSetup.getProperties()
    }

    void "test kafka stream application"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 30)

        when:
        testKafkaClient.publishText("Test message")

        then:
        conditions.eventually {
            kafkaListener.text.contains("Test message")
            exporter.finishedSpanItems.name.any { it.contains("publish") }
        }
    }


    @KafkaClient
    static interface TestKafkaClient {

        @Topic("my-stream")
        void publishText(String s);
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class TestKafkaListener {

        private final List<String> text = new ArrayList<>()

        @Topic("my-stream")
        void updateAnalytics(String s) {
            text.add(s)
        }
    }

}
