package io.micronaut.tracing.instrument.util

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.core.publisher.SignalType
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Consumer

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN
import static io.micronaut.tracing.instrument.util.MDCSpec.RequestIdFilter.TRACE_ID_MDC_KEY

@Slf4j("LOG")
class MDCSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'mdc.test.enabled': true,
    ])

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void 'test MDC does not leak'() {
        given:
        LOG.info('MDC adapter: {}', MDC.getMDCAdapter())

        expect:
        100.times {
            String traceId = UUID.randomUUID()
            HttpRequest<Object> request = HttpRequest
                    .GET('/mdc-test')
                    .header('traceId', traceId)
            String response = client.toBlocking().retrieve(request)
            assert response == traceId
        }
    }

    @Controller
    @Requires(property = 'mdc.test.enabled')
    static class MDCController {

        @Get('/mdc-test')
        HttpResponse<String> getMdc() {
            Map<String, String> mdc = MDC.getCopyOfContextMap() ?: [:]
            String traceId = mdc[TRACE_ID_MDC_KEY]
            LOG.info('traceId: {}', traceId)
            if (traceId == null) {
                throw new IllegalStateException('Missing traceId')
            }
            HttpResponse.ok(traceId)
        }
    }

    @Slf4j("LOG")
    @Filter(MATCH_ALL_PATTERN)
    @Requires(property = 'mdc.test.enabled')
    static class RequestIdFilter implements HttpServerFilter {

        private static final String TRACE_ID_MDC_KEY = 'traceId'

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request,
                                                   ServerFilterChain chain) {

            String traceIdHeader = request.headers.get('traceId')
            if (MDC.get(TRACE_ID_MDC_KEY) != null) {
                LOG.warn('MDC should have been empty here.')
            }

            LOG.info('Storing traceId in MDC: {}', traceIdHeader)
            MDC.put(TRACE_ID_MDC_KEY, traceIdHeader)
            LOG.info('MDC updated')

            return Flux
                    .from(chain.proceed(request))
                    .doFinally(new Consumer<SignalType>() {
                        @Override
                        void accept(SignalType signalType) {
                            LOG.info('Removing traceId id from MDC')
                            MDC.clear()
                        }
                    })
        }

        @Override
        int getOrder() {
            return -1
        }
    }
}
