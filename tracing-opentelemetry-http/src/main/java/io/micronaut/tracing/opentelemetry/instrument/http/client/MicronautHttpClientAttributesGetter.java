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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MutableHttpRequest;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;

import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpAttributes.SERVICE_ID;
import static io.micronaut.http.HttpVersion.HTTP_1_0;
import static io.micronaut.http.HttpVersion.HTTP_1_1;
import static io.micronaut.http.HttpVersion.HTTP_2_0;

@Internal
enum MicronautHttpClientAttributesGetter implements HttpClientAttributesGetter<MutableHttpRequest<Object>, HttpResponse<Object>> {

    INSTANCE;

    private static final Map<HttpVersion, String> PROTOCOL_VERSION = Map.of(HTTP_1_0, "1.0", HTTP_1_1, "1.1", HTTP_2_0, "2.0");

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
        return PROTOCOL_VERSION.get(request.getHttpVersion());
    }

    @Override
    @Nullable
    public String getServerAddress(MutableHttpRequest<Object> request) {
        return request.getAttribute(SERVICE_ID, String.class)
            .filter(serviceId -> !serviceId.contains("/"))
            .orElseGet(() -> request.getRemoteAddress().getHostString());
    }

    @Override
    @Nullable
    public Integer getServerPort(MutableHttpRequest<Object> request) {
        return request.getServerAddress().getPort();
    }
}
