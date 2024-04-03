package io.micronaut.tracing.opentelemetry.instrument.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.util.KafkaSetup
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.testcontainers.containers.KafkaContainer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class KafkaTelemetryIntegrationSpec extends Specification {

    PollingConditions conditions = new PollingConditions(timeout: 60, delay: 1)

    void "test kafka stream application"() {
        given:
        KafkaContainer kafkaContainer = KafkaSetup.init()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'kafka.enabled': 'true',
                "kafka.bootstrap.servers": kafkaContainer.bootstrapServers,
        ])
        def context = embeddedServer.applicationContext
        TestKafkaClient testKafkaClient = context.getBean(TestKafkaClient)
        TestKafkaListener kafkaListener = context.getBean(TestKafkaListener)
        def exporter = context.getBean(InMemorySpanExporter)

        when:
        testKafkaClient.publishText("Test message")

        then:
        conditions.eventually {
            kafkaListener.text.size() == 1
            exporter.getFinishedSpanItems().size() == 2
            exporter.finishedSpanItems.name.any(x -> x.contains("publish"))
            exporter.finishedSpanItems.name.any(x -> x.contains("process"))
        }

        cleanup:
        KafkaSetup.destroy()
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
