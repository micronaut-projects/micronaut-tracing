package io.micronaut.tracing.opentelemetry.instrument.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.opentelemetry.api.common.AttributeType
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.internal.InternalAttributeKeyImpl
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.header.internals.RecordHeaders
import spock.lang.Specification

class KafkaTelemetryFactorySpec extends Specification {

    void "test kafka telemetry headers config with custom prefix"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                getConfiguration() + [
                        "otel.instrumentation.kafka.enabled"              : "true",
                        "otel.instrumentation.kafka.attribute-with-prefix": "true",
                        "otel.instrumentation.kafka.attribute-prefix"     : "myPrefix.",
                        "otel.instrumentation.kafka.captured-headers"     : [
                                "test",
                                "myHeader"
                        ]
                ])

        when:
        def kafkaTelemetryFactory = ctx.getBean(KafkaTelemetryFactory)
        def kafkaTelemetryProperties = ctx.getBean(KafkaTelemetryProperties)

        then:
        kafkaTelemetryFactory
        kafkaTelemetryProperties

        when:
        def attributesBuilder = Attributes.builder()
        def headers = new RecordHeaders()
        headers.add("test", "myTest".bytes)
        headers.add("myHeader", "myValue".bytes)
        headers.add("myHeader2", "myValue2".bytes)
        KafkaAttributesExtractorUtils.putAttributes(kafkaTelemetryProperties, attributesBuilder, headers)

        then:
        def attrs = attributesBuilder.build()
        attrs.get(InternalAttributeKeyImpl.create("myPrefix.test", AttributeType.STRING)) == "myTest"
        attrs.get(InternalAttributeKeyImpl.create("myPrefix.myHeader", AttributeType.STRING)) == "myValue"
        !attrs.get(InternalAttributeKeyImpl.create("myPrefix.myHeader2", AttributeType.STRING))

        cleanup:
        ctx.close()
    }

    void "test kafka telemetry headers config with default prefix"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                getConfiguration() + [
                        "otel.instrumentation.kafka.enabled"              : "true",
                        "otel.instrumentation.kafka.attribute-with-prefix": "true",
                        "otel.instrumentation.kafka.captured-headers"     : [
                                "test",
                                "myHeader"
                        ]
                ])

        when:
        def kafkaTelemetryFactory = ctx.getBean(KafkaTelemetryFactory)
        def kafkaTelemetryProperties = ctx.getBean(KafkaTelemetryProperties)

        then:
        kafkaTelemetryFactory
        kafkaTelemetryProperties

        when:
        def attributesBuilder = Attributes.builder()
        def headers = new RecordHeaders()
        headers.add("test", "myTest".bytes)
        headers.add("myHeader", "myValue".bytes)
        headers.add("myHeader2", "myValue2".bytes)
        KafkaAttributesExtractorUtils.putAttributes(kafkaTelemetryProperties, attributesBuilder, headers)

        then:
        def attrs = attributesBuilder.build()
        attrs.get(InternalAttributeKeyImpl.create("messaging.header.test", AttributeType.STRING)) == "myTest"
        attrs.get(InternalAttributeKeyImpl.create("messaging.header.myHeader", AttributeType.STRING)) == "myValue"
        !attrs.get(InternalAttributeKeyImpl.create("messaging.header.myHeader2", AttributeType.STRING))

        cleanup:
        ctx.close()
    }

    void "test kafka telemetry headers config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                getConfiguration() + [
                        "otel.instrumentation.kafka.enabled"         : "true",
                        "otel.instrumentation.kafka.captured-headers": [
                                "test",
                                "myHeader"
                        ]
                ])

        when:
        def kafkaTelemetryFactory = ctx.getBean(KafkaTelemetryFactory)
        def kafkaTelemetryProperties = ctx.getBean(KafkaTelemetryProperties)

        then:
        kafkaTelemetryFactory
        kafkaTelemetryProperties

        when:
        def attributesBuilder = Attributes.builder()
        def headers = new RecordHeaders()
        headers.add("test", "myTest".bytes)
        headers.add("myHeader", "myValue".bytes)
        headers.add("myHeader2", "myValue2".bytes)
        KafkaAttributesExtractorUtils.putAttributes(kafkaTelemetryProperties, attributesBuilder, headers)

        then:
        def attrs = attributesBuilder.build()
        attrs.get(InternalAttributeKeyImpl.create("test", AttributeType.STRING)) == "myTest"
        attrs.get(InternalAttributeKeyImpl.create("myHeader", AttributeType.STRING)) == "myValue"
        !attrs.get(InternalAttributeKeyImpl.create("myHeader2", AttributeType.STRING))

        cleanup:
        ctx.close()
    }

    void "test kafka telemetry all headers config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                getConfiguration() + [
                        "otel.instrumentation.kafka.enabled": "true",
                ])

        when:
        def kafkaTelemetryFactory = ctx.getBean(KafkaTelemetryFactory)
        def kafkaTelemetryProperties = ctx.getBean(KafkaTelemetryProperties)

        then:
        kafkaTelemetryFactory
        kafkaTelemetryProperties

        when:
        def attributesBuilder = Attributes.builder()
        def headers = new RecordHeaders()
        headers.add("test", "myTest".bytes)
        headers.add("myHeader", "myValue".bytes)
        headers.add("myHeader2", "myValue2".bytes)
        KafkaAttributesExtractorUtils.putAttributes(kafkaTelemetryProperties, attributesBuilder, headers)

        then:
        def attrs = attributesBuilder.build()
        attrs.get(InternalAttributeKeyImpl.create("test", AttributeType.STRING)) == "myTest"
        attrs.get(InternalAttributeKeyImpl.create("myHeader", AttributeType.STRING)) == "myValue"
        attrs.get(InternalAttributeKeyImpl.create("myHeader2", AttributeType.STRING)) == "myValue2"

        cleanup:
        ctx.close()
    }

    void "test kafka telemetry none headers config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                getConfiguration() + [
                        "otel.instrumentation.kafka.enabled"         : "true",
                        "otel.instrumentation.kafka.captured-headers": [
                                ''
                        ]
                ])

        when:
        def kafkaTelemetryFactory = ctx.getBean(KafkaTelemetryFactory)
        def kafkaTelemetryProperties = ctx.getBean(KafkaTelemetryProperties)

        then:
        kafkaTelemetryFactory
        kafkaTelemetryProperties

        when:
        def attributesBuilder = Attributes.builder()
        def headers = new RecordHeaders()
        headers.add("test", "myTest".bytes)
        headers.add("myHeader", "myValue".bytes)
        headers.add("myHeader2", "myValue2".bytes)
        KafkaAttributesExtractorUtils.putAttributes(kafkaTelemetryProperties, attributesBuilder, headers)

        then:
        def attrs = attributesBuilder.build()
        !attrs.get(InternalAttributeKeyImpl.create("test", AttributeType.STRING))
        !attrs.get(InternalAttributeKeyImpl.create("myHeader", AttributeType.STRING))
        !attrs.get(InternalAttributeKeyImpl.create("myHeader2", AttributeType.STRING))

        cleanup:
        ctx.close()
    }

    protected Map<String, Object> getConfiguration() {
        ['spec.name': getClass().simpleName]
    }

    @Requires(property = 'spec.name', value = 'KafkaTelemetryFactorySpec')
    @Singleton
    static class MyClass {
        @Inject
        @KafkaClient("foo")
        Producer<String, Integer> producer
    }
}

