package io.micronaut.tracing.opentelemetry.instrument.jms

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.jms.CompletionListener
import jakarta.jms.Destination
import jakarta.jms.Message
import jakarta.jms.MessageProducer
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The asynchronous sends keep the argument validation of the provider.
 */
class TracingMessageProducerSpec extends Specification {

    InMemorySpanExporter exporter = InMemorySpanExporter.create()

    @AutoCleanup
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
        .build()

    @Unroll
    void 'an asynchronous #overload send with a null completion listener is rejected by the provider'() {
        given:
        // a provider rejects a null completion listener, as Jakarta Messaging requires
        MessageProducer delegate = Mock()
        delegate.send(*_) >> { List args ->
            if (args.last() == null) {
                throw new IllegalArgumentException('completionListener')
            }
        }
        def producer = new TracingMessageProducer(delegate, new JmsTelemetry(openTelemetry))
        def destination = Mock(Destination)
        def message = Mock(Message)

        when:
        send(producer, destination, message)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'completionListener'
        exporter.finishedSpanItems.empty

        where:
        overload                         | send
        'message'                        | { p, d, m -> p.send(m, (CompletionListener) null) }
        'message with options'           | { p, d, m -> p.send(m, 1, 4, 0L, (CompletionListener) null) }
        'destination'                    | { p, d, m -> p.send(d, m, (CompletionListener) null) }
        'destination with options'       | { p, d, m -> p.send(d, m, 1, 4, 0L, (CompletionListener) null) }
    }
}
