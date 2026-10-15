package io.micronaut.tracing.opentracing.instrument.http

import io.micronaut.context.ApplicationContext
import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryExclusionsConfiguration
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Predicate

class OpenTelemetryExclusionsConfigurationSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            'otel.exclusions[0]': '.*pattern.*',
            'otel.exclusions[1]': '/literal',
    )

    OpenTelemetryExclusionsConfiguration configuration = context.getBean(OpenTelemetryExclusionsConfiguration)

    Predicate<String> pathPredicate = configuration.exclusionTest()

    @Unroll
    void 'path #path is #desc by predicate'() {
        expect:
        pathPredicate.test(path) == excluded

        where:
        path            | excluded
        '/some/pattern' | true
        '/patterns'     | true
        '/another'      | false
        '/literal'      | true
        '/literal/sub'  | false

        desc = excluded ? 'excluded' : 'included'
    }

    @Unroll
    void 'combined patterns #patterns match #path as each pattern on its own'() {
        given:
        def config = new OpenTelemetryExclusionsConfiguration()
        config.exclusions = patterns

        expect:
        config.exclusionTest().test(path) == patterns.any { path ==~ it }

        where:
        patterns                                  | path
        ['/a', '/b|/c']                           | '/c'
        ['/a', '/b|/c']                           | '/a/c'
        ['(?i)/health', '/metrics']               | '/HEALTH'
        ['(?i)/health', '/metrics']               | '/METRICS'
        ['/(x)\\1', '/(y)']                       | '/xx'
        ['/(x)\\1', '/(y)']                       | '/y'
        ['/(?<n>a)', '/(?<n>b)']                  | '/b'
        ['/\\Q*', '/z']                           | '/*'
        ['/\\Q*', '/z']                           | '/z'
        ['(?x) /a  # comment', '/b']              | '/a'
        ['(?x) /a  # comment', '/b']              | '/b'
    }
}
