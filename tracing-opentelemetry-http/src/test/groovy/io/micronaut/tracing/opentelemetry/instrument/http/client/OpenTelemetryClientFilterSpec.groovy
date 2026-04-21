package io.micronaut.tracing.opentelemetry.instrument.http.client

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import reactor.core.publisher.Mono
import spock.lang.Specification

class OpenTelemetryClientFilterSpec extends Specification {

    void 'falls back to the original request when the instrumenter returns no context'() {
        given:
        MutableHttpRequest<Object> request = HttpRequest.GET('/test')
        HttpResponse<Object> response = HttpResponse.ok()
        ClientFilterChain chain = Mock()
        Instrumenter<MutableHttpRequest<?>, Object> instrumenter = Mock()
        def filter = new OpenTelemetryClientFilter(null, instrumenter)

        when:
        HttpResponse<?> result = Mono.from(filter.doFilter(request, chain)).block()

        then:
        1 * instrumenter.shouldStart(_, request) >> true
        1 * instrumenter.start(_, request) >> null
        1 * chain.proceed(request) >> Mono.just(response)
        0 * instrumenter.end(_, _, _, _)
        result == response
    }
}
