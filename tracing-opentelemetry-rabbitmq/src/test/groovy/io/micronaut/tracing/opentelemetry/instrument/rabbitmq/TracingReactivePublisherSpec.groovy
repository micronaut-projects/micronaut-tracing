package io.micronaut.tracing.opentelemetry.instrument.rabbitmq

import com.rabbitmq.client.AMQP
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.rabbitmq.reactive.RabbitPublishState
import io.micronaut.rabbitmq.reactive.ReactivePublisher
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class TracingReactivePublisherSpec extends Specification {

    void "publish injects trace headers and emits a producer span"() {
        given:
        def exporter = InMemorySpanExporter.create()
        OpenTelemetry openTelemetry = openTelemetry(exporter)
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

    void "publish does not start a span before subscription"() {
        given:
        def exporter = InMemorySpanExporter.create()
        def spanProcessor = new CountingSpanProcessor()
        def telemetry = new RabbitMQTelemetry(openTelemetry(exporter, spanProcessor))
        def delegate = Mock(ReactivePublisher)
        def publisher = new TracingReactivePublisher(delegate, telemetry)
        def publishState = new RabbitPublishState("orders", "created", false, new AMQP.BasicProperties(), "hello".bytes)

        when:
        def result = publisher.publish(publishState)

        then:
        0 * delegate._
        spanProcessor.started.get() == 0
        spanProcessor.ended.get() == 0
        exporter.finishedSpanItems.empty

        when:
        Mono.from(result).block()

        then:
        1 * delegate.publish(_) >> Mono.empty()
        spanProcessor.started.get() == 1
        spanProcessor.ended.get() == 1
        exporter.finishedSpanItems.size() == 1
    }

    void "publish and confirm emits a producer span"() {
        given:
        def exporter = InMemorySpanExporter.create()
        def telemetry = new RabbitMQTelemetry(openTelemetry(exporter))
        def delegate = Mock(ReactivePublisher)
        def publisher = new TracingReactivePublisher(delegate, telemetry)
        def publishState = new RabbitPublishState("", "created", true, new AMQP.BasicProperties(), "hello".bytes)

        when:
        Mono.from(publisher.publishAndConfirm(publishState)).block()

        then:
        1 * delegate.publishAndConfirm(_) >> Mono.empty()
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].attributes.asMap().values().contains("created")
    }

    void "publish and reply returns consumer state and emits a producer span"() {
        given:
        def exporter = InMemorySpanExporter.create()
        def telemetry = new RabbitMQTelemetry(openTelemetry(exporter))
        def delegate = Mock(ReactivePublisher)
        def publisher = new TracingReactivePublisher(delegate, telemetry)
        def publishState = new RabbitPublishState("orders", "created", false, new AMQP.BasicProperties.Builder().headers([existing: "value"]).build(), "hello".bytes)
        def replyState = Mock(io.micronaut.rabbitmq.bind.RabbitConsumerState)

        when:
        def result = Mono.from(publisher.publishAndReply(publishState)).block()

        then:
        result.is(replyState)
        1 * delegate.publishAndReply(_) >> { RabbitPublishState state ->
            assert state.properties.headers.existing == "value"
            assert state.properties.headers.traceparent
            Mono.just(replyState)
        }
        exporter.finishedSpanItems.size() == 1
    }

    void "synchronous publish failure records span error"() {
        given:
        def exporter = InMemorySpanExporter.create()
        def telemetry = new RabbitMQTelemetry(openTelemetry(exporter))
        def delegate = Mock(ReactivePublisher)
        def publisher = new TracingReactivePublisher(delegate, telemetry)
        def publishState = new RabbitPublishState("orders", "created", false, new AMQP.BasicProperties(), "hello".bytes)
        def failure = new IllegalStateException("boom")

        when:
        Mono.from(publisher.publish(publishState)).block()

        then:
        1 * delegate.publish(_) >> { throw failure }
        def e = thrown(IllegalStateException)
        e.is(failure)
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].status.statusCode.name() == "ERROR"
    }

    void "asynchronous publish failure records span error"() {
        given:
        def exporter = InMemorySpanExporter.create()
        def telemetry = new RabbitMQTelemetry(openTelemetry(exporter))
        def delegate = Mock(ReactivePublisher)
        def publisher = new TracingReactivePublisher(delegate, telemetry)
        def publishState = new RabbitPublishState("orders", "created", false, new AMQP.BasicProperties(), "hello".bytes)

        when:
        Mono.from(publisher.publish(publishState)).block()

        then:
        1 * delegate.publish(_) >> Mono.error(new IllegalArgumentException("boom"))
        thrown(IllegalArgumentException)
        exporter.finishedSpanItems.size() == 1
        exporter.finishedSpanItems[0].status.statusCode.name() == "ERROR"
    }

    void "reactive publisher instrumentation preserves existing tracing wrapper"() {
        given:
        def telemetry = new RabbitMQTelemetry(openTelemetry(InMemorySpanExporter.create()))
        def delegate = Mock(ReactivePublisher)
        def publisher = new TracingReactivePublisher(delegate, telemetry)
        def instrumentation = new RabbitMQReactivePublisherTracingInstrumentation(telemetry)
        def event = Stub(BeanCreatedEvent) {
            getBean() >> publisher
        }

        expect:
        instrumentation.onCreated(event).is(publisher)
    }

    private static OpenTelemetry openTelemetry(InMemorySpanExporter exporter) {
        openTelemetry(exporter, [] as SpanProcessor[])
    }

    private static OpenTelemetry openTelemetry(InMemorySpanExporter exporter, SpanProcessor... spanProcessors) {
        def tracerProviderBuilder = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        spanProcessors.each { tracerProviderBuilder.addSpanProcessor(it) }
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProviderBuilder.build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.instance))
            .build()
    }

    private static final class CountingSpanProcessor implements SpanProcessor {
        final AtomicInteger started = new AtomicInteger()
        final AtomicInteger ended = new AtomicInteger()

        @Override
        void onStart(io.opentelemetry.context.Context parentContext, ReadWriteSpan span) {
            started.incrementAndGet()
        }

        @Override
        boolean isStartRequired() {
            true
        }

        @Override
        void onEnd(ReadableSpan span) {
            ended.incrementAndGet()
        }

        @Override
        boolean isEndRequired() {
            true
        }
    }
}
