package io.micronaut.tracing.instrument.http

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Predicate

class TracingExclusionsConfigurationSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            'tracing.exclusions[0]': '.*pattern.*',
            'tracing.exclusions[1]': '/literal',
    )

    TracingExclusionsConfiguration configuration = context.getBean(TracingExclusionsConfiguration)

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
}
