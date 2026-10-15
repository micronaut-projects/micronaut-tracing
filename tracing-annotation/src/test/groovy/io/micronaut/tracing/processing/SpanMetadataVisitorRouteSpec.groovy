package io.micronaut.tracing.processing

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.tracing.util.SpanMetadata
import spock.lang.Specification

/**
 * The route methods of the Kotlin (KSP) controllers, whose language cannot be compiled in this module.
 */
class SpanMetadataVisitorRouteSpec extends Specification {

    private static final String HTTP_METHOD_MAPPING = 'io.micronaut.http.annotation.HttpMethodMapping'

    void 'the visitor supports the route annotations'() {
        expect:
        new SpanMetadataVisitor().supportedAnnotationNames.containsAll([
            'io.micronaut.http.annotation.Controller',
            HTTP_METHOD_MAPPING,
            'io.opentelemetry.instrumentation.annotations.AddingSpanAttributes'
        ])
    }

    void 'a Kotlin route method with a mangled name gets the source name'() {
        given:
        MethodElement element = Mock()
        AnnotationValue<SpanMetadata> metadata = null

        when:
        new SpanMetadataVisitor().visitMethod(element, context(VisitorContext.Language.KOTLIN))

        then:
        _ * element.isStatic() >> false
        _ * element.isPrivate() >> false
        _ * element.hasAnnotation(_ as Class) >> false
        _ * element.getName() >> 'book-Aa0_--Z'
        _ * element.hasStereotype(HTTP_METHOD_MAPPING) >> true
        _ * element.getNativeType() >> new Object()
        1 * element.annotate(SpanMetadata, _) >> { Class<SpanMetadata> type, consumer ->
            AnnotationValueBuilder<SpanMetadata> builder = AnnotationValue.builder(type)
            consumer.accept(builder)
            metadata = builder.build()
            element
        }
        metadata.stringValue(SpanMetadata.MEMBER_METHOD).get() == 'book'
    }

    void 'other route methods get no span metadata'() {
        given:
        MethodElement element = Mock()

        when:
        new SpanMetadataVisitor().visitMethod(element, context(language))

        then:
        _ * element.isStatic() >> isStatic
        _ * element.isPrivate() >> false
        _ * element.hasAnnotation(_ as Class) >> false
        _ * element.getName() >> name
        _ * element.hasStereotype(HTTP_METHOD_MAPPING) >> route
        0 * element.annotate(*_)

        where:
        language                         | name           | route | isStatic
        VisitorContext.Language.JAVA     | 'book-Aa0_--Z' | true  | false
        VisitorContext.Language.GROOVY   | 'book-Aa0_--Z' | true  | false
        VisitorContext.Language.KOTLIN   | 'book'         | true  | false
        VisitorContext.Language.KOTLIN   | 'book-Aa0_--Z' | false | false
        VisitorContext.Language.KOTLIN   | 'book-Aa0_--Z' | true  | true
    }

    private VisitorContext context(VisitorContext.Language language) {
        return Stub(VisitorContext) {
            getLanguage() >> language
        }
    }
}
