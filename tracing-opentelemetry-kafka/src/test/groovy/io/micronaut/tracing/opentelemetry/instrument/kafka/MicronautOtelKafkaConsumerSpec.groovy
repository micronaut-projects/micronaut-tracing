package io.micronaut.tracing.opentelemetry.instrument.kafka

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.SubscriptionPattern
import org.apache.kafka.common.TopicPartition
import spock.lang.Specification

import java.time.Duration

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

    def subscriptionPattern = Mock(SubscriptionPattern)
    def consumerRebalanceListener = Mock(ConsumerRebalanceListener)

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
        micronautConsumer.close((Duration) null)

        then:
        1 * consumer.close({ CloseOptions closeOptions -> !closeOptions.timeout().isPresent() })

        when:
        micronautConsumer.close(Duration.ofSeconds(1))

        then:
        1 * consumer.close({ CloseOptions closeOptions -> closeOptions.timeout().orElseThrow() == Duration.ofSeconds(1) })

        when:
        micronautConsumer.close((CloseOptions) null)

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
        micronautConsumer.subscribe(subscriptionPattern)

        then:
        1 * consumer.subscribe(subscriptionPattern)

        when:
        micronautConsumer.subscribe(subscriptionPattern, consumerRebalanceListener)

        then:
        1 * consumer.subscribe(subscriptionPattern, consumerRebalanceListener)

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
        micronautConsumer.registerMetricForSubscription(null)

        then:
        1 * consumer.registerMetricForSubscription(null)

        when:
        micronautConsumer.unregisterMetricFromSubscription(null)

        then:
        1 * consumer.unregisterMetricFromSubscription(null)

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

    void "traced records activate context from partition and topic iterators and close stale context"() {
        given:
        def processInstrumenter = Mock(Instrumenter)
        def configuration = Mock(KafkaTelemetryConfiguration)
        def kafkaTelemetry = new KafkaTelemetry(
                Mock(OpenTelemetry),
                Mock(Instrumenter),
                processInstrumenter,
                new ArrayList<KafkaTelemetryProducerTracingFilter>(),
                new ArrayList<KafkaTelemetryConsumerTracingFilter>(),
                configuration,
                true
        )
        def micronautConsumer = new MicronautOtelKafkaConsumer(consumer, kafkaTelemetry)
        def partition = new TopicPartition("topic", 0)
        def firstRecord = new ConsumerRecord<String, String>("topic", 0, 0, "key", "first")
        def secondRecord = new ConsumerRecord<String, String>("topic", 0, 1, "key", "second")
        def records = new ConsumerRecords<String, String>([(partition): [firstRecord, secondRecord]])
        ContextKey<String> contextKey = ContextKey.named("test-kafka-context")

        configuration.getIncludedTopics() >> Collections.emptyList()
        configuration.getExcludedTopics() >> Collections.emptyList()
        consumer.poll(Duration.ZERO) >> records
        consumer.groupMetadata() >> new ConsumerGroupMetadata("group")
        consumer.metrics() >> Collections.emptyMap()
        processInstrumenter.shouldStart(_, _) >> true
        processInstrumenter.start(_, _) >> Context.current().with(contextKey, "active")

        when:
        def tracedRecords = micronautConsumer.poll(Duration.ZERO)
        def partitionIterator = tracedRecords.records(partition).iterator()
        def partitionRecord = partitionIterator.next()

        then:
        partitionRecord == firstRecord
        Context.current().get(contextKey) == "active"

        when:
        tracedRecords.iterator()

        then:
        Context.current().get(contextKey) == null
        1 * processInstrumenter.end(_, _, null, null)

        when:
        def topicIterator = tracedRecords.records("topic").iterator()
        def topicRecord = topicIterator.next()

        then:
        topicRecord == firstRecord
        Context.current().get(contextKey) == "active"

        when:
        topicIterator.next()

        then:
        Context.current().get(contextKey) == "active"
        1 * processInstrumenter.end(_, _, null, null)

        when:
        boolean hasNext = topicIterator.hasNext()

        then:
        !hasNext
        Context.current().get(contextKey) == null
        1 * processInstrumenter.end(_, _, null, null)
    }

}
