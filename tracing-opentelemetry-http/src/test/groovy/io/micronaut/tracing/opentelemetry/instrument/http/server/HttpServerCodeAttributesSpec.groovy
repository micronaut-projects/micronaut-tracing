package io.micronaut.tracing.opentelemetry.instrument.http.server

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.CodeAttributes
import io.opentelemetry.semconv.HttpAttributes
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class HttpServerCodeAttributesSpec extends Specification {

    private static final String SPEC_NAME = 'HttpServerCodeAttributesSpec'
    private static final AttributeKey<String> CUSTOM = AttributeKey.stringKey('test.custom.code')

    @AutoCleanup
    ApplicationContext context

    @AutoCleanup
    HttpClient client

    InMemorySpanExporter exporter

    private void start(Map<String, Object> properties = [:]) {
        context = ApplicationContext.run([
            'spec.name'                                         : SPEC_NAME,
            'otel.register.global'                              : false,
            'micronaut.application.name'                        : 'test-app',
            'micronaut.router.static-resources.default.paths'   : 'classpath:code-attributes-public',
            'micronaut.router.static-resources.default.mapping' : '/static/**'
        ] + properties, Environment.TEST)
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        client = HttpClient.create(server.URL)
        exporter = context.getBean(InMemorySpanExporter)
    }

    private SpanData serverSpan() {
        new PollingConditions(timeout: 10).eventually {
            assert exporter.finishedSpanItems.any { it.kind == SpanKind.SERVER }
        }
        return exporter.finishedSpanItems.find { it.kind == SpanKind.SERVER }
    }

    void 'the server span of a Java controller route has code.function.name'() {
        given:
        start()

        when:
        String body = client.toBlocking().retrieve('/code-java/books/1')

        then:
        body == 'book 1'
        def span = serverSpan()
        span.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == CodeAttributesJavaController.name + '.book'
        span.attributes.get(HttpAttributes.HTTP_ROUTE) == '/code-java/books/{id}'
        span.attributes.get(HttpServerCodeAttributesExtractor.CODE_NAMESPACE) == null
        span.attributes.get(HttpServerCodeAttributesExtractor.CODE_FUNCTION) == null
    }

    void 'the server span of a Groovy controller route has code.function.name, computed once per route'() {
        given:
        start()

        when:
        client.toBlocking().retrieve('/code-groovy/hello')
        client.toBlocking().retrieve('/code-groovy/hello')

        then:
        new PollingConditions(timeout: 10).eventually {
            def spans = exporter.finishedSpanItems.findAll { it.kind == SpanKind.SERVER }
            assert spans.size() == 2
            assert spans.every { it.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == GroovyController.name + '.hello' }
        }
    }

    void 'the server span of an unmatched request has no code attributes'() {
        given:
        start()

        when:
        client.toBlocking().retrieve('/code-missing')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
        def span = serverSpan()
        span.attributes.get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE) == 404L
        span.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == null
    }

    void 'the server span of a static resource has no code attributes'() {
        given:
        start()

        when:
        String body = client.toBlocking().retrieve('/static/hello.txt')

        then:
        body.trim() == 'static'
        def span = serverSpan()
        span.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == null
    }

    void 'the code attributes can be disabled'() {
        given:
        start((OpenTelemetryHttpServerCodeAttributesConfig.ENABLED): false)

        when:
        client.toBlocking().retrieve('/code-java/books/1')

        then:
        !context.containsBean(HttpServerCodeAttributesExtractor)
        serverSpan().attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == null
    }

    void 'the deprecated code attributes can also be added'() {
        given:
        start((OpenTelemetryHttpServerCodeAttributesConfig.PREFIX + '.legacy-attributes'): true)

        when:
        client.toBlocking().retrieve('/code-java/books/1')

        then:
        def span = serverSpan()
        span.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == CodeAttributesJavaController.name + '.book'
        span.attributes.get(HttpServerCodeAttributesExtractor.CODE_NAMESPACE) == CodeAttributesJavaController.name
        span.attributes.get(HttpServerCodeAttributesExtractor.CODE_FUNCTION) == 'book'
    }

    void 'the code attributes extractor can be replaced'() {
        given:
        start('spec.replace-code-attributes': true)

        when:
        client.toBlocking().retrieve('/code-java/books/1')

        then:
        def span = serverSpan()
        span.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) == null
        span.attributes.get(CUSTOM) == 'custom'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/code-groovy')
    static class GroovyController {

        @Get('/hello')
        String hello() {
            'hello'
        }
    }

    @Singleton
    @MicronautHttpServerTelemetryFactory.Server
    @Replaces(HttpServerCodeAttributesExtractor)
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Requires(property = 'spec.replace-code-attributes', value = 'true')
    static class CustomCodeAttributesExtractor implements AttributesExtractor<HttpRequest<Object>, HttpResponse<Object>> {

        @Override
        void onStart(AttributesBuilder attributes, Context parentContext, HttpRequest<Object> request) {
            attributes.put(CUSTOM, 'custom')
        }

        @Override
        void onEnd(AttributesBuilder attributes, Context context, HttpRequest<Object> request, HttpResponse<Object> response, Throwable error) {
        }
    }
}
