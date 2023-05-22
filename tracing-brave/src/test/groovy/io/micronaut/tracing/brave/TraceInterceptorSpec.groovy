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

    private void buildContext() {
        applicationContext = ApplicationContext
                .builder('tracing.zipkin.enabled': true,
                         'tracing.zipkin.sampler.probability': 1)
                .singletons(new TestReporter())
                .start()
        tracedService = applicationContext.getBean(TracedService)
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

        @NewSpan('trace-cs')
        CompletableFuture<String> futureTrace(@SpanTag('more.stuff') String name) {
            return CompletableFuture.completedFuture(name).thenApply({ String v ->
                spanCustomizer.tag('foo', 'bar')
                return v
            })
        }
    }
}
