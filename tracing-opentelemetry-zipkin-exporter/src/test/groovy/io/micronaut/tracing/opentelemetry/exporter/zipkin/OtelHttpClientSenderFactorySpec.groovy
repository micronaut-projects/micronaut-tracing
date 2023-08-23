package io.micronaut.tracing.opentelemetry.exporter.zipkin


import io.micronaut.context.BeanContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import jakarta.inject.Inject
import spock.lang.Specification
import zipkin2.reporter.Sender


@MicronautTest(startApplication = false)
class OtelHttpClientSenderFactorySpec extends Specification {

    @Inject
    BeanContext beanContext

    void "if you have micronaut otel Zipkin export module verify that Sender and SpanProcessor exists"() {

        expect:
        beanContext.containsBean(Sender)
        beanContext.containsBean(SpanProcessor)

        when:
        Sender sender = beanContext.getBean(Sender)
        SpanProcessor spanProcessor = beanContext.getBean(SpanProcessor)

        then:
        sender instanceof HttpClientSender
        spanProcessor instanceof BatchSpanProcessor
    }
}
