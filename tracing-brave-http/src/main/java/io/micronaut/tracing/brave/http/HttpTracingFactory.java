/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tracing.brave.http;

import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Adds HTTP tracing for Micronaut using Brave.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(beans = Tracing.class)
@Requires(classes = HttpTracing.class)
public class HttpTracingFactory {

    /**
     * The {@code HttpTracing} bean.
     *
     * @param tracing the {@code Tracing} bean
     * @return the {@code HttpTracing} bean
     */
    @Singleton
    @Requires(missingBeans = HttpTracing.class)
    HttpTracing httpTracing(Tracing tracing) {
        return HttpTracing.create(tracing);
    }

    /**
     * The {@code HttpClientHandler} bean.
     *
     * @param httpTracing the {@code HttpTracing} bean
     * @return the {@code HttpClientHandler} bean
     */
    @Singleton
    HttpClientHandler<HttpClientRequest, HttpClientResponse> httpClientHandler(HttpTracing httpTracing) {
        return HttpClientHandler.create(httpTracing);
    }

    /**
     * The {@code HttpServerHandler} bean.
     *
     * @param httpTracing the {@code HttpTracing} bean
     * @return the {@code HttpServerHandler} bean
     */
    @Singleton
    HttpServerHandler<HttpServerRequest, HttpServerResponse> httpServerHandler(HttpTracing httpTracing) {
        return HttpServerHandler.create(httpTracing);
    }
}
