package io.micronaut.tracing.jms.app

import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.annotations.Topic
import io.micronaut.messaging.annotation.MessageBody
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.opentelemetry.api.trace.Span
import jakarta.inject.Singleton

import java.util.concurrent.ConcurrentHashMap

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME

// The test beans live outside the instrumentation package, so that disabling the instrumentation
// (a package requirement) does not disable them.

@Requires(property = "spec.name", value = "JmsTelemetrySpec")
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface TextProducer {

    @Queue("queue_text")
    void send(@MessageBody String body)

    @Topic("topic_text")
    void publish(@MessageBody String body)

    @Queue("queue_failing")
    void fail(@MessageBody String body)
}

@Requires(property = "spec.name", value = "JmsTelemetrySpec")
@Singleton
class TextService {

    private final TextProducer producer

    TextService(TextProducer producer) {
        this.producer = producer
    }

    @NewSpan("send-text")
    void send(String body) {
        producer.send(body)
    }
}

@Requires(property = "spec.name", value = "JmsTelemetrySpec")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class TextListener {

    final List<String> messages = [].asSynchronized()
    final Map<String, String> spanIds = new ConcurrentHashMap<>()
    final Map<String, String> propagatedSpanIds = new ConcurrentHashMap<>()

    @Queue("queue_text")
    void receive(@MessageBody String body) {
        record(body)
    }

    @Topic("topic_text")
    void receiveTopic(@MessageBody String body) {
        record(body)
    }

    @Queue("queue_failing")
    void receiveFailing(@MessageBody String body) {
        throw new IllegalStateException("Failed to process " + body)
    }

    private void record(String body) {
        spanIds[body] = Span.current().spanContext.spanId
        PropagatedContext.find()
                .flatMap { it.find(OpenTelemetryPropagationContext) }
                .ifPresent { propagatedSpanIds[body] = Span.fromContext(it.context()).spanContext.spanId }
        messages << body
    }
}
