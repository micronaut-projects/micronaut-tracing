package io.micronaut.tracing.opentelemetry.instrument.kafka

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * The spans of records sent and processed without a Kafka client instance.
 */
class KafkaTelemetrySpanSpec extends Specification {

    InMemorySpanExporter exporter = InMemorySpanExporter.create()

    @AutoCleanup
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build()

    KafkaTelemetry telemetry = KafkaTelemetry.create(openTelemetry, new KafkaTelemetryConfiguration(), [], [])

    void 'a producer record sent by client id has a producer span whose context is injected into the record'() {
        given:
        def record = new ProducerRecord<String, String>('orders', 'key', 'value')

        when:
        telemetry.buildAndInjectSpan(record, 'client-1')

        then:
        exporter.finishedSpanItems.size() == 1
        with(exporter.finishedSpanItems[0]) {
            kind == SpanKind.PRODUCER
            name == 'orders publish'
            new String(record.headers().lastHeader('traceparent').value()).contains(traceId)
        }
    }

    void 'consumer records processed by consumer group have a consumer span each'() {
        given:
        def records = [
            new ConsumerRecord<String, String>('orders', 0, 0L, 'key', 'one'),
            new ConsumerRecord<String, String>('orders', 0, 1L, 'key', 'two')
        ]

        when:
        telemetry.buildAndFinishSpan(records, 'group-1', 'client-1')

        then:
        exporter.finishedSpanItems.size() == 2
        exporter.finishedSpanItems.every { it.kind == SpanKind.CONSUMER && it.name == 'orders process' }
    }
}
