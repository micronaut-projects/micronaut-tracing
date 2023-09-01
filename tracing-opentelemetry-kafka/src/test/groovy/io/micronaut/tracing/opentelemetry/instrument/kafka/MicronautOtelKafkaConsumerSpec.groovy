package io.micronaut.tracing.opentelemetry.instrument.kafka

import io.opentelemetry.api.OpenTelemetry
import org.apache.kafka.clients.consumer.Consumer
import spock.lang.Specification

class MicronautOtelKafkaConsumerSpec extends Specification {

    def consumer = Mock(Consumer)

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
        def micronautConsumer = new MicronautOtelKafkaConsumer(consumer, kafkaTelemetry)

        when:
        micronautConsumer.assignment()

        then:
        1 * consumer.assignment()

        when:
        micronautConsumer.assign(null)

        then:
        1 * consumer.assign(null)

        when:
        micronautConsumer.close()

        then:
        1 * consumer.close()

        when:
        micronautConsumer.close(null)

        then:
        1 * consumer.close(null)

        when:
        micronautConsumer.commitAsync()

        then:
        1 * consumer.commitAsync()

        when:
        micronautConsumer.commitAsync(null, null)

        then:
        1 * consumer.commitAsync(null, null)

        when:
        micronautConsumer.commitAsync(null)

        then:
        1 * consumer.commitAsync(null)

        when:
        micronautConsumer.beginningOffsets(null)

        then:
        1 * consumer.beginningOffsets(null)

        when:
        micronautConsumer.beginningOffsets(null, null)

        then:
        1 * consumer.beginningOffsets(null, null)

        when:
        micronautConsumer.commitSync()

        then:
        1 * consumer.commitSync()

        when:
        micronautConsumer.commitSync(null)

        then:
        1 * consumer.commitSync(null)

        when:
        micronautConsumer.commitSync(null, null)

        then:
        1 * consumer.commitSync(null, null)

        when:
        micronautConsumer.committed(null, null)

        then:
        1 * consumer.committed(null, null)

        when:
        micronautConsumer.committed(null)

        then:
        1 * consumer.committed(null)

        when:
        micronautConsumer.subscription()

        then:
        1 * consumer.subscription()

        when:
        micronautConsumer.subscribe(null)

        then:
        1 * consumer.subscribe(null)

        when:
        micronautConsumer.subscribe(null, null)

        then:
        1 * consumer.subscribe(null, null)

        when:
        micronautConsumer.seek(null, 1)

        then:
        1 * consumer.seek(null, 1)

        when:
        micronautConsumer.seek(null, null)

        then:
        1 * consumer.seek(null, null)

        when:
        micronautConsumer.position(null, null)

        then:
        1 * consumer.position(null, null)

        when:
        micronautConsumer.position(null)

        then:
        1 * consumer.position(null)

        when:
        micronautConsumer.metrics()

        then:
        1 * consumer.metrics()

        when:
        micronautConsumer.partitionsFor(null)

        then:
        1 * consumer.partitionsFor(null)

        when:
        micronautConsumer.partitionsFor(null, null)

        then:
        1 * consumer.partitionsFor(null, null)

        when:
        micronautConsumer.listTopics(null)

        then:
        1 * consumer.listTopics(null)

        when:
        micronautConsumer.listTopics()

        then:
        1 * consumer.listTopics()

        when:
        micronautConsumer.paused()

        then:
        1 * consumer.paused()

        when:
        micronautConsumer.pause(null)

        then:
        1 * consumer.pause(null)

        when:
        micronautConsumer.resume(null)

        then:
        1 * consumer.resume(null)

        when:
        micronautConsumer.offsetsForTimes(null)

        then:
        1 * consumer.offsetsForTimes(null)

        when:
        micronautConsumer.offsetsForTimes(null, null)

        then:
        1 * consumer.offsetsForTimes(null, null)

        when:
        micronautConsumer.endOffsets(null, null)

        then:
        1 * consumer.endOffsets(null, null)

        when:
        micronautConsumer.endOffsets(null)

        then:
        1 * consumer.endOffsets(null)

        when:
        micronautConsumer.currentLag(null)

        then:
        1 * consumer.currentLag(null)

        when:
        micronautConsumer.groupMetadata()

        then:
        1 * consumer.groupMetadata()

        when:
        micronautConsumer.enforceRebalance()

        then:
        1 * consumer.enforceRebalance()

        when:
        micronautConsumer.enforceRebalance(null)

        then:
        1 * consumer.enforceRebalance(null)

        when:
        micronautConsumer.wakeup()

        then:
        1 * consumer.wakeup()

        when:
        micronautConsumer.seekToBeginning(null)

        then:
        1 * consumer.seekToBeginning(null)

        when:
        micronautConsumer.seekToEnd(null)

        then:
        1 * consumer.seekToEnd(null)

        when:
        micronautConsumer.committed(null)

        then:
        1 * consumer.committed(null)

        when:
        micronautConsumer.committed(null, null)

        then:
        1 * consumer.committed(null, null)

        when:
        micronautConsumer.unsubscribe()

        then:
        1 * consumer.unsubscribe()
    }

}
