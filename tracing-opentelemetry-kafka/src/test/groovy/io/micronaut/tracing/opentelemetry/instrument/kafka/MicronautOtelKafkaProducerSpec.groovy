package io.micronaut.tracing.opentelemetry.instrument.kafka

import io.opentelemetry.api.OpenTelemetry
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import spock.lang.Specification

class MicronautOtelKafkaProducerSpec extends Specification {

    def producer = Mock(Producer)

    def kafkaTelemetry = new KafkaTelemetry(
            Mock(OpenTelemetry),
            Mock(io.opentelemetry.instrumentation.api.instrumenter.Instrumenter),
            Mock(io.opentelemetry.instrumentation.api.instrumenter.Instrumenter),
            new ArrayList<KafkaTelemetryProducerTracingFilter>(),
            new ArrayList<KafkaTelemetryConsumerTracingFilter>(),
            Mock(KafkaTelemetryConfiguration),
            true
    )

    void "test otel kafka consumer wrapper" () {
        def micronautProducer = new MicronautOtelKafkaProducer(producer, kafkaTelemetry)

        when:
        micronautProducer.metrics()

        then:
        1 * producer.metrics()

        when:
        micronautProducer.close(null)

        then:
        1 * producer.close(null)

        when:
        micronautProducer.close()

        then:
        1 * producer.close()

        when:
        micronautProducer.abortTransaction()

        then:
        1 * producer.abortTransaction()

        when:
        micronautProducer.beginTransaction()

        then:
        1 * producer.beginTransaction()

        when:
        micronautProducer.commitTransaction()

        then:
        1 * producer.commitTransaction()

        when:
        micronautProducer.flush()

        then:
        1 * producer.flush()

        when:
        micronautProducer.initTransactions()

        then:
        1 * producer.initTransactions()

        when:
        micronautProducer.partitionsFor(null)

        then:
        1 * producer.partitionsFor(null)

        when:
        micronautProducer.sendOffsetsToTransaction(null, "test")

        then:
        1 * producer.sendOffsetsToTransaction(null, "test")

    }

}
