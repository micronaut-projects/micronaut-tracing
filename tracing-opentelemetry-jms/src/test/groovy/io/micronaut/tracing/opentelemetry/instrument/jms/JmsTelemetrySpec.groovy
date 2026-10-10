package io.micronaut.tracing.opentelemetry.instrument.jms

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.jms.listener.JMSListenerRegistry
import io.micronaut.jms.pool.JMSConnectionPool
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.tracing.jms.app.TextListener
import io.micronaut.tracing.jms.app.TextProducer
import io.micronaut.tracing.jms.app.TextService
import io.micronaut.tracing.opentelemetry.test.TestSpans
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME

@MicronautTest
@Property(name = "spec.name", value = "JmsTelemetrySpec")
class JmsTelemetrySpec extends Specification implements TestPropertyProvider {

    static final AttributeKey<String> MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system")
    static final AttributeKey<String> MESSAGING_DESTINATION_NAME = AttributeKey.stringKey("messaging.destination.name")
    static final AttributeKey<String> MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation")
    static final AttributeKey<String> MESSAGING_OPERATION_NAME = AttributeKey.stringKey("messaging.operation.name")
    static final AttributeKey<String> MESSAGING_MESSAGE_ID = AttributeKey.stringKey("messaging.message.id")

    @Inject
    TestSpans spans

    @Inject
    TextProducer producer

    @Inject
    TextService textService

    @Inject
    TextListener listener

    @Inject
    ApplicationContext context

    PollingConditions conditions = new PollingConditions(timeout: 30)

    void setup() {
        listener.messages.clear()
    }

    @Override
    Map<String, String> getProperties() {
        brokerProperties()
    }

    static Map<String, String> brokerProperties() {
        String broker = UUID.randomUUID().toString().replace('-', '')
        [
                'micronaut.jms.activemq.classic.enabled'         : 'true',
                'micronaut.jms.activemq.classic.connection-string': "vm://${broker}?broker.persistent=false&broker.useJmx=false".toString(),
                'micronaut.jms.activemq.classic.username'        : 'activemq',
                'micronaut.jms.activemq.classic.password'        : 'activemq',
                'otel.register.global'                           : 'false',
                'otel.traces.exporter'                           : 'none',
        ]
    }

    void "the pool and the listener registry are instrumented"() {
        expect:
        context.getBean(JMSConnectionPool, Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME)) instanceof TracingJMSConnectionPool
        context.getBean(JMSListenerRegistry) instanceof TracingJMSListenerRegistry
    }

    void "a message sent inside a span is processed by the listener in the same trace"() {
        when:
        textService.send("hello")
        List<SpanData> finished = awaitSpans { List<SpanData> s -> s.any { it.kind == SpanKind.CONSUMER } }
        SpanData parent = finished.find { it.name.endsWith("send-text") }
        SpanData publish = finished.find { it.kind == SpanKind.PRODUCER }
        SpanData process = finished.find { it.kind == SpanKind.CONSUMER }

        then:
        listener.messages == ["hello"]
        parent != null
        publish.name == "queue_text publish"
        publish.traceId == parent.traceId
        publish.parentSpanId == parent.spanId
        process.name == "queue_text process"
        process.traceId == publish.traceId
        process.parentSpanId == publish.spanId
        process.parentSpanContext.remote

        and: 'the messaging attributes are recorded'
        publish.attributes.get(MESSAGING_SYSTEM) == "jms"
        publish.attributes.get(MESSAGING_DESTINATION_NAME) == "queue_text"
        operation(publish) == "publish"
        publish.attributes.get(MESSAGING_MESSAGE_ID) != null
        process.attributes.get(MESSAGING_SYSTEM) == "jms"
        process.attributes.get(MESSAGING_DESTINATION_NAME) == "queue_text"
        operation(process) == "process"
        process.attributes.get(MESSAGING_MESSAGE_ID) == publish.attributes.get(MESSAGING_MESSAGE_ID)

        and: 'the consumer span is current in the listener, also through the propagated context'
        listener.spanIds["hello"] == process.spanId
        listener.propagatedSpanIds["hello"] == process.spanId
    }

    void "a message sent outside a span starts a new trace"() {
        when:
        producer.send("root")
        List<SpanData> finished = awaitSpans { List<SpanData> s -> s.any { it.kind == SpanKind.CONSUMER } }
        SpanData publish = finished.find { it.kind == SpanKind.PRODUCER }
        SpanData process = finished.find { it.kind == SpanKind.CONSUMER }

        then:
        !publish.parentSpanContext.valid
        process.parentSpanId == publish.spanId
    }

    void "messages sent to a topic are traced"() {
        when:
        producer.publish("news")
        List<SpanData> finished = awaitSpans { List<SpanData> s -> s.any { it.kind == SpanKind.CONSUMER } }
        SpanData publish = finished.find { it.kind == SpanKind.PRODUCER }
        SpanData process = finished.find { it.kind == SpanKind.CONSUMER }

        then:
        listener.messages == ["news"]
        publish.name == "topic_text publish"
        publish.attributes.get(MESSAGING_DESTINATION_NAME) == "topic_text"
        process.name == "topic_text process"
        process.parentSpanId == publish.spanId
    }

    void "an error thrown by the listener is recorded on the consumer span"() {
        when:
        producer.fail("boom")
        List<SpanData> finished = awaitSpans { List<SpanData> s -> s.any { it.kind == SpanKind.CONSUMER } }
        SpanData publish = finished.find { it.kind == SpanKind.PRODUCER }
        SpanData process = finished.find { it.kind == SpanKind.CONSUMER }

        then:
        process.name == "queue_failing process"
        process.parentSpanId == publish.spanId
        process.status.statusCode == StatusCode.ERROR
        process.events.any { it.name == "exception" }
        publish.status.statusCode == StatusCode.UNSET
    }

    void "the instrumentation can be disabled"() {
        given:
        ApplicationContext disabled = ApplicationContext.run(brokerProperties() + [
                'spec.name'                       : 'JmsTelemetrySpec',
                'otel.instrumentation.jms.enabled': 'false',
        ])
        TestSpans testSpans = disabled.getBean(TestSpans)
        TextListener disabledListener = disabled.getBean(TextListener)

        when:
        disabled.getBean(TextProducer).send("untraced")

        then:
        conditions.eventually {
            assert disabledListener.messages == ["untraced"]
        }
        !disabled.containsBean(JmsTelemetry)
        !(disabled.getBean(JMSConnectionPool, Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME)) instanceof TracingJMSConnectionPool)
        !(disabled.getBean(JMSListenerRegistry) instanceof TracingJMSListenerRegistry)
        testSpans.finishedSpans().findAll { it.kind in [SpanKind.PRODUCER, SpanKind.CONSUMER] }.isEmpty()

        cleanup:
        disabled?.close()
    }

    private List<SpanData> awaitSpans(Closure<Boolean> condition) {
        long deadline = System.nanoTime() + TestSpans.DEFAULT_TIMEOUT.toNanos() * 3
        while (System.nanoTime() < deadline) {
            List<SpanData> finished = spans.finishedSpans()
            if (condition.call(finished)) {
                return finished
            }
            Thread.sleep(50)
        }
        throw new AssertionError("Spans not finished: " + spans.finishedSpans())
    }

    private static String operation(SpanData span) {
        span.attributes.get(MESSAGING_OPERATION_NAME) ?: span.attributes.get(MESSAGING_OPERATION)
    }
}
