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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings({"rawtypes", "unused"})
enum MicronautHttpClientAttributesGetter implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

    INSTANCE;

    @Override
    public String method(HttpRequest request) {
        return request.getMethodName();
    }

    @Override
    public String url(HttpRequest request) {
        return request.getUri().toString();
    }

    @Override
    public List<String> requestHeader(HttpRequest request, String name) {
        return request.getHeaders().getAll(name);
    }

    @Override
    public Long requestContentLength(HttpRequest request, @Nullable HttpResponse response) {
        return request.getContentLength();
    }

    @Override
    @Nullable
    public Long requestContentLengthUncompressed(HttpRequest request, @Nullable HttpResponse response) {
        return null;
    }

    @Override
    @SuppressWarnings("UnnecessaryDefaultInEnumSwitch")
    @Nullable
    public String flavor(HttpRequest request, @Nullable HttpResponse response) {
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

    @Override
    public Integer statusCode(HttpRequest request, HttpResponse response) {
        return response.code();
    }

    @Override
    public Long responseContentLength(HttpRequest request, HttpResponse response) {
        return response.getContentLength();
    }

    @Override
    @Nullable
    public Long responseContentLengthUncompressed(HttpRequest request, HttpResponse response) {
        return null;
    }

    @Override
    public List<String> responseHeader(HttpRequest request, HttpResponse response, String name) {
        return response.getHeaders().getAll(name);
    }

}
