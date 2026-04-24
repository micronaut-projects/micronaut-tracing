package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

import com.rabbitmq.client.AMQP
import io.micronaut.rabbitmq.reactive.RabbitPublishState
import io.micronaut.rabbitmq.reactive.ReactivePublisher
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import reactor.core.publisher.Mono
import spock.lang.Specification

class TracingReactivePublisherSpec extends Specification {

    void "publish injects trace headers and emits a producer span"() {
        given:
        def exporter = InMemorySpanExporter.create()
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.instance))
                .build()
        def telemetry = new RabbitMQTelemetry(openTelemetry)
        def delegate = Mock(ReactivePublisher)
        def publisher = new TracingReactivePublisher(delegate, telemetry)
        def publishState = new RabbitPublishState("orders", "created", false, new AMQP.BasicProperties(), "hello".bytes)

        when:
        Mono.from(publisher.publish(publishState)).block()

        then:
        1 * delegate.publish(_) >> { RabbitPublishState state ->
            assert state.properties.headers.traceparent
            Mono.empty()
        }
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].kind.name() == "PRODUCER"
        exporter.finishedSpanItems[0].attributes.asMap().values().contains("rabbitmq")
    }
}
