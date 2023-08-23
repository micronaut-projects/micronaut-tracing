/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.opentelemetry.exporter.zipkin;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.http.client.HttpClientConfiguration;

import static io.micronaut.tracing.opentelemetry.exporter.zipkin.HttpClientOtelSenderConfiguration.PREFIX;

/**
 * Configuration properties for Zipkin exporter.
 */
@ConfigurationProperties(PREFIX)
public class HttpClientOtelSenderConfiguration extends HttpClientConfiguration {

    public static final String PREFIX =  "otel.exporter.zipkin";

    @ConfigurationBuilder(prefixes = "")
    protected final HttpClientSender.Builder clientSenderBuilder;

    /**
     * Initialize the builder with client configurations.
     */
    public HttpClientOtelSenderConfiguration() {
        clientSenderBuilder = new HttpClientSender.Builder(this);
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return new ConnectionPoolConfiguration();
    }

    /**
     * Creates builder.
     *
     * @return the builder
     */
    public HttpClientSender.Builder getBuilder() {
        return clientSenderBuilder;
    }
}
