package io.micronaut.tracing.brave

import brave.SpanCustomizer
import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * @author graemerocher
 * @since 1.0
 */
class TraceInterceptorSpec extends Specification {

    @AutoCleanup
    private ApplicationContext applicationContext
    private TracedService tracedService
    private ClassLevelNewSpanService classLevelNewSpanService
    private InterfaceLevelNewSpanService interfaceLevelNewSpanService
    private TestReporter reporter

    void 'test trace interceptor'() {
        when:
        buildContext()
        String result = tracedService.methodOne('test')

        then:
        result == 'test'
        reporter.spans.size() == 2

        reporter.spans[0].name() == 'trace-rx'
        reporter.spans[0].tags()['more.stuff'] == 'test'
        reporter.spans[0].tags()['class'] == 'TracedService'
        reporter.spans[0].tags()['method'] == 'methodThree'

        reporter.spans[1].name() == 'my-trace'
        reporter.spans[1].tags()['foo.bar'] == 'test'
    }

    void 'test trace completable future'() {
        when:
        buildContext()
        String result = tracedService.futureTrace('test').get()

        then:
        result == 'test'
        reporter.spans.size() == 1

        reporter.spans[0].name() == 'trace-cs'
        reporter.spans[0].tags()['more.stuff'] == 'test'
        reporter.spans[0].tags()['class'] == 'TracedService'
        reporter.spans[0].tags()['method'] == 'futureTrace'
        reporter.spans[0].tags()['foo'] == 'bar'
    }

    void 'test trace interceptor NewSpan without name'() {
        when:
        buildContext()
        String result = tracedService.noArgNewSpan('test')

        then:
        result == 'test'
        reporter.spans.size() == 1

        reporter.spans[0].name() == 'tracedservice.noargnewspan'
        reporter.spans[0].tags()['more.stuff'] == 'test'
        reporter.spans[0].tags()['class'] == 'TracedService'
        reporter.spans[0].tags()['method'] == 'noArgNewSpan'
    }

    void 'test NewSpan declared on classes and interfaces'() {
        when:
        buildContext()
        def classLevel = classLevelNewSpanService.classLevel()
        def classLevelOverride = classLevelNewSpanService.classLevelOverride()
        def interfaceLevel = interfaceLevelNewSpanService.interfaceLevel()
        def interfaceLevelOverride = interfaceLevelNewSpanService.interfaceLevelOverride()

        then:
        classLevel == 'class-level'
        classLevelOverride == 'class-level-override'
        interfaceLevel == 'interface-level'
        interfaceLevelOverride == 'interface-level-override'
        reporter.spans.size() == 4
        def spanNames = reporter.spans.collect { it.name() }
        spanNames.contains('classlevelnewspanservice.classlevel')
        spanNames.contains('class-level-override')
        spanNames.contains('interfacelevelnewspanserviceimpl.interfacelevel')
        spanNames.contains('interface-level-override')
    }

    private void buildContext() {
        applicationContext = ApplicationContext
                .builder('tracing.zipkin.enabled': true,
                         'tracing.zipkin.sampler.probability': 1)
                .singletons(new TestReporter())
                .start()
        tracedService = applicationContext.getBean(TracedService)
        classLevelNewSpanService = applicationContext.getBean(ClassLevelNewSpanService)
        interfaceLevelNewSpanService = applicationContext.getBean(InterfaceLevelNewSpanService)
        reporter = applicationContext.getBean(TestReporter)
    }

    @Singleton
    static class TracedService {

        @Inject
        SpanCustomizer spanCustomizer

        @NewSpan('my-trace')
        String methodOne(@SpanTag('foo.bar') String name) {
            methodTwo(name)
        }

        @ContinueSpan
        String methodTwo(@SpanTag('foo.baz') String another) {
            Mono.from(methodThree(another)).block()
        }

        @NewSpan('trace-rx')
        @SingleResult
        Publisher<String> methodThree(@SpanTag('more.stuff') String name) {
            return Mono.just(name)
        }

        @NewSpan()
        @SingleResult
        String noArgNewSpan(@SpanTag('more.stuff') String name) {
            return name
        }

        @NewSpan('trace-cs')
        CompletableFuture<String> futureTrace(@SpanTag('more.stuff') String name) {
            return CompletableFuture.completedFuture(name).thenApply({ String v ->
                spanCustomizer.tag('foo', 'bar')
                return v
            })
        }
    }

    @Singleton
    @NewSpan
    static class ClassLevelNewSpanService {

        String classLevel() {
            return 'class-level'
        }

        @NewSpan('class-level-override')
        String classLevelOverride() {
            return 'class-level-override'
        }
    }

    @NewSpan
    static interface InterfaceLevelNewSpanService {

        String interfaceLevel()

        String interfaceLevelOverride()
    }

    @Singleton
    static class InterfaceLevelNewSpanServiceImpl implements InterfaceLevelNewSpanService {

        @Override
        String interfaceLevel() {
            return 'interface-level'
        }

        @Override
        @NewSpan('interface-level-override')
        String interfaceLevelOverride() {
            return 'interface-level-override'
        }
    }
}
