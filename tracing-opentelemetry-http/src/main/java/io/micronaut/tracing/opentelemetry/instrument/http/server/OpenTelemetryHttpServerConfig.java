package io.micronaut.tracing.opentelemetry.instrument.http.server;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;


@ConfigurationProperties(value = OpenTelemetryHttpServerConfig.PREFIX)
public class OpenTelemetryHttpServerConfig {
    public static final String PREFIX = "otel.http.server";

    private List<String> responseHeaders = new ArrayList<>();
    private List<String> requestHeaders = new ArrayList<>();

    public List<String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(List<String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public List<String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(List<String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }
}
