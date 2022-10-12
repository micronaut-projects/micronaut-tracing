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
import io.micronaut.http.MutableHttpRequest;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.List;

@Internal
enum MicronautHttpClientAttributesGetter implements HttpClientAttributesGetter<MutableHttpRequest<Object>, HttpResponse<Object>> {

    INSTANCE;

    @Override
    public String method(MutableHttpRequest<Object> request) {
        return request.getMethodName();
    }

    @Override
    public List<String> requestHeader(MutableHttpRequest<Object> request, String name) {
        return request.getHeaders().getAll(name);
    }

    @Override
    public Integer statusCode(MutableHttpRequest<Object> request, HttpResponse<Object> response, @Nullable Throwable error) {
        return response.code();
    }

    @Override
    public List<String> responseHeader(MutableHttpRequest<Object> request, HttpResponse<Object> response, String name) {
        return response.getHeaders().getAll(name);
    }

    @Override
    public String url(MutableHttpRequest<Object> request) {
        return request.getUri().toString();
    }

    @Override
    @Nullable
    public String flavor(MutableHttpRequest<Object> request, @Nullable HttpResponse<Object> response) {
        switch (request.getHttpVersion()) {
            case HTTP_1_0:
                return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
            case HTTP_1_1:
                return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
            case HTTP_2_0:
                return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
            default:
                return null;
        }
    }
}
