package io.micronaut.tracing.opentelemetry.instrument.http

import io.micronaut.context.ApplicationContext
import io.micronaut.tracing.opentelemetry.instrument.http.client.OpenTelemetryHttpClientConfig
import io.micronaut.tracing.opentelemetry.instrument.http.server.OpenTelemetryHttpServerConfig
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

class OpentelemtryHttpConfigurationSpec extends Specification {

    String CLIENT_REQUEST_HEADER = "X-CLIENT-REQUEST-HEADER"
    String CLIENT_RESPONSE_HEADER = "X-CLIENT-RESPONSE-HEADER"
    String SERVER_REQUEST_HEADER = "X-SERVER-REQUEST-HEADER"
    String SERVER_RESPONSE_HEADER = "X-SERVER-RESPONSE-HEADER"


    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            'otel.http.client.request-headers': [CLIENT_REQUEST_HEADER],
            'otel.http.client.response-headers': [CLIENT_RESPONSE_HEADER],
            'otel.http.server.request-headers': [SERVER_REQUEST_HEADER],
            'otel.http.server.response-headers': [SERVER_RESPONSE_HEADER]
    )

    OpenTelemetryHttpServerConfig serverConfig = context.getBean(OpenTelemetryHttpServerConfig)
    OpenTelemetryHttpClientConfig clientConfig = context.getBean(OpenTelemetryHttpClientConfig)

    @Unroll
    void 'path #path is #desc by predicate'() {
        expect:
        clientConfig.getRequestHeaders().size() == 1
        clientConfig.getRequestHeaders().get(0) == CLIENT_REQUEST_HEADER
        clientConfig.getResponseHeaders().size() == 1
        clientConfig.getResponseHeaders().get(0) == CLIENT_RESPONSE_HEADER
        serverConfig.getRequestHeaders().size() == 1
        serverConfig.getRequestHeaders().get(0) == SERVER_REQUEST_HEADER
        serverConfig.getResponseHeaders().size() == 1
        serverConfig.getResponseHeaders().get(0) == SERVER_RESPONSE_HEADER
    }
}
