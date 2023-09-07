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
package io.micronaut.tracing.opentelemetry.instrument.http.client;

import java.util.List;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;

@Internal
enum MicronautHttpClientAttributesGetter implements HttpClientAttributesGetter<MutableHttpRequest<Object>, HttpResponse<Object>> {

    INSTANCE;

    @Override
    public String getHttpRequestMethod(MutableHttpRequest<Object> request) {
        return request.getMethodName();
    }

    @Override
    public List<String> getHttpRequestHeader(MutableHttpRequest<Object> request, String name) {
        return request.getHeaders().getAll(name);
    }

    @Override
    public Integer getHttpResponseStatusCode(MutableHttpRequest<Object> request, HttpResponse<Object> response, @Nullable Throwable error) {
        return response.code();
    }

    @Override
    public List<String> getHttpResponseHeader(MutableHttpRequest<Object> request, HttpResponse<Object> response, String name) {
        return response.getHeaders().getAll(name);
    }

    @Override
    public String getUrlFull(MutableHttpRequest<Object> request) {
        return request.getUri().toString();
    }

    @Override
    @Nullable
    public String getNetworkProtocolVersion(MutableHttpRequest<Object> request, @Nullable HttpResponse<Object> response) {
        return switch (request.getHttpVersion()) {
            case HTTP_1_0 -> "1.0";
            case HTTP_1_1 -> "1.1";
            case HTTP_2_0 -> "2.0";
        };
    }

    @Override
    @Nullable
    public String getServerAddress(MutableHttpRequest<Object> request) {
        return request.getServerAddress().getHostName();
    }

    @Override
    @Nullable
    public Integer getServerPort(MutableHttpRequest<Object> request) {
        return request.getServerAddress().getPort();
    }
}
