package io.micronaut.tracing.opentelemetry.instrument.http.server

import io.micronaut.http.HttpRequest
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.MethodExecutionHandle
import io.micronaut.web.router.MethodBasedRouteInfo
import io.micronaut.web.router.RouteAttributes
import io.micronaut.web.router.RouteInfo
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.context.Context
import io.opentelemetry.semconv.CodeAttributes
import spock.lang.Specification

class HttpServerCodeAttributesExtractorSpec extends Specification {

    void 'the configuration defaults and setters'() {
        when:
        def config = new OpenTelemetryHttpServerCodeAttributesConfig()

        then:
        config.enabled == OpenTelemetryHttpServerCodeAttributesConfig.DEFAULT_ENABLED
        config.legacyAttributes == OpenTelemetryHttpServerCodeAttributesConfig.DEFAULT_LEGACY_ATTRIBUTES

        when:
        config.enabled = false
        config.legacyAttributes = true

        then:
        !config.enabled
        config.legacyAttributes
    }

    void 'the extractor works without a configuration and adds nothing on end'() {
        given:
        def extractor = new HttpServerCodeAttributesExtractor(null)
        def route = route('book')
        AttributesBuilder attributes = Attributes.builder()

        when:
        extractor.onStart(attributes, Context.root(), request(route))
        extractor.onEnd(attributes, Context.root(), request(route), null, null)

        then:
        def built = attributes.build()
        built.size() == 1
        built.get(CodeAttributes.CODE_FUNCTION_NAME) == HttpServerCodeAttributesExtractorSpec.name + '.book'
    }

    void 'the error routes and the routes that are not method based have no code attributes'() {
        given:
        def extractor = new HttpServerCodeAttributesExtractor(null)
        MethodBasedRouteInfo errorRoute = Stub {
            isErrorRoute() >> true
        }
        RouteInfo otherRoute = Stub()
        AttributesBuilder attributes = Attributes.builder()

        when:
        extractor.onStart(attributes, Context.root(), request(errorRoute))
        extractor.onStart(attributes, Context.root(), request(otherRoute))
        extractor.onStart(attributes, Context.root(), HttpRequest.GET('/none'))

        then:
        attributes.build().isEmpty()
    }

    void 'the code function of a route is resolved once'() {
        given:
        def extractor = new HttpServerCodeAttributesExtractor(null)
        ExecutableMethod method = Stub {
            getDeclaringType() >> HttpServerCodeAttributesExtractorSpec
            getMethodName() >> 'book'
            getAnnotation(_ as Class) >> null
        }
        MethodExecutionHandle handle = Mock()
        MethodBasedRouteInfo route = Stub {
            getTargetMethod() >> handle
        }

        when:
        3.times {
            extractor.onStart(Attributes.builder(), Context.root(), request(route))
        }
        // a concurrent request that resolved the route first
        def resolved = extractor.resolve(route)

        then:
        1 * handle.getExecutableMethod() >> method
        resolved.name() == HttpServerCodeAttributesExtractorSpec.name + '.book'
    }

    void 'the cache of the code functions is bounded'() {
        given:
        def extractor = new HttpServerCodeAttributesExtractor(null)

        when:
        List<String> names = (0..1100).collect {
            AttributesBuilder attributes = Attributes.builder()
            extractor.onStart(attributes, Context.root(), request(route('m' + it)))
            attributes.build().get(CodeAttributes.CODE_FUNCTION_NAME)
        }

        then:
        names.every { it.startsWith(HttpServerCodeAttributesExtractorSpec.name + '.m') }
        names.last() == HttpServerCodeAttributesExtractorSpec.name + '.m1100'
        extractor.@functions.get().size() == 1024
    }

    private MethodBasedRouteInfo route(String methodName) {
        ExecutableMethod method = Stub {
            getDeclaringType() >> HttpServerCodeAttributesExtractorSpec
            getMethodName() >> methodName
            getAnnotation(_ as Class) >> null
        }
        MethodExecutionHandle handle = Stub {
            getExecutableMethod() >> method
        }
        return Stub(MethodBasedRouteInfo) {
            getTargetMethod() >> handle
        }
    }

    private static HttpRequest<Object> request(RouteInfo<?> route) {
        HttpRequest<Object> request = HttpRequest.GET('/test')
        RouteAttributes.setRouteInfo(request, route)
        return request
    }
}
