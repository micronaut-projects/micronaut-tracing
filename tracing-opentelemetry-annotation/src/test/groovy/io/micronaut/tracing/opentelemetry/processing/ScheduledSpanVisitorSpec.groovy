package io.micronaut.tracing.opentelemetry.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.tracing.opentelemetry.annotation.ScheduledSpan

class ScheduledSpanVisitorSpec extends AbstractTypeElementSpec {

    void 'the visitor is isolating and supports the scheduling annotations'() {
        given:
        def visitor = new ScheduledSpanVisitor()

        expect:
        visitor.visitorKind == TypeElementVisitor.VisitorKind.ISOLATING
        visitor.supportedAnnotationNames == [
            'io.micronaut.scheduling.annotation.Scheduled',
            'io.micronaut.scheduling.annotation.Schedules'
        ] as Set
    }

    void 'the scheduled methods that can be intercepted are traced'() {
        given:
        def definition = buildBeanDefinition('test.Job', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
class Job {

    @Scheduled(fixedDelay = "1s")
    public void single() {
    }

    @Scheduled(fixedDelay = "1s")
    @Scheduled(cron = "* * * * * *")
    public void repeated() {
    }

    @Scheduled(fixedDelay = "1s")
    public final void finalMethod() {
    }

    @Executable
    public void notScheduled() {
    }
}
''')

        expect:
        definition.findMethod('single').get().hasAnnotation(ScheduledSpan)
        definition.findMethod('repeated').get().hasAnnotation(ScheduledSpan)
        !definition.findMethod('finalMethod').get().hasAnnotation(ScheduledSpan)
        !definition.findMethod('notScheduled').get().hasAnnotation(ScheduledSpan)
    }

    void 'the scheduled methods of a final class are not traced'() {
        given:
        def definition = buildBeanDefinition('test.FinalJob', '''
package test;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
final class FinalJob {

    @Scheduled(fixedDelay = "1s")
    public void run() {
    }
}
''')

        expect:
        !definition.findMethod('run').get().hasAnnotation(ScheduledSpan)
    }
}
