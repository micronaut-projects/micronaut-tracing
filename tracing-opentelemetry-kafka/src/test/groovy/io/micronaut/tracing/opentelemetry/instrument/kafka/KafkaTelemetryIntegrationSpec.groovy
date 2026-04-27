package io.micronaut.tracing.opentelemetry.instrument.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.util.KafkaSetup
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import org.apache.kafka.clients.consumer.ConsumerRecord
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

@MicronautTest
class KafkaTelemetryIntegrationSpec extends Specification implements TestPropertyProvider {

    @Inject TestKafkaClient testKafkaClient
    @Inject TestKafkaListener kafkaListener
    @Inject TestTracingService testTracingService
    @Inject InMemorySpanExporter exporter

    @Override
    Map<String, String> getProperties() {
        // This triggers the container start and topic creation
        return KafkaSetup.getProperties()
    }

    void "test kafka stream application"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 30)
        String message = "Test message ${System.nanoTime()}"

        when:
        testTracingService.publishText(message)

        then:
        conditions.eventually {
            kafkaListener.text.contains(message)
            kafkaListener.propagatedTraceId == testTracingService.traceId
            kafkaListener.traceId == testTracingService.traceId
            kafkaListener.traceparent
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
        String traceId
        String traceparent
        String propagatedTraceId

        @Topic("my-stream")
        void updateAnalytics(ConsumerRecord<?, String> record) {
            text.add(record.value())
            traceId = Span.current().spanContext.traceId
            traceparent = record.headers().lastHeader("traceparent") != null ? new String(record.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8) : null
            propagatedTraceId = traceparent?.split('-')?.length > 1 ? traceparent.split('-')[1] : null
        }
    }

    static class TestTracingService {

        @Inject TestKafkaClient testKafkaClient
        String traceId

        @NewSpan("publish")
        void publishText(String message) {
            traceId = Span.current().spanContext.traceId
            testKafkaClient.publishText(message)
        }
    }

}
