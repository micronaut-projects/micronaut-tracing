package io.micronaut.tracing.opentelemetry.instrument.http.client;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;


@ConfigurationProperties(value = OpenTelemetryHttpClientConfig.PREFIX)
public class OpenTelemetryHttpClientConfig {
    public static final String PREFIX = "otel.http.client";

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
