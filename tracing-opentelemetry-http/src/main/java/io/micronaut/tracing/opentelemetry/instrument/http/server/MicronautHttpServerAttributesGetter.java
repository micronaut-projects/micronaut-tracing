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
package io.micronaut.tracing.opentelemetry.instrument.http.server;

import java.util.List;
import java.util.Optional;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.web.router.UriRouteInfo;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

@Internal
enum MicronautHttpServerAttributesGetter implements HttpServerAttributesGetter<HttpRequest<Object>, HttpResponse<Object>> {

    INSTANCE;

    @Override
    public String getMethod(HttpRequest<Object> request) {
        return request.getMethodName();
    }

    @Override
    public List<String> getRequestHeader(HttpRequest<Object> request, String name) {
        return request.getHeaders().getAll(name);
    }

    @Override
    public Integer getStatusCode(HttpRequest<Object> request, HttpResponse<Object> response, @Nullable Throwable error) {
        return response.code();
    }

    @Override
    public List<String> getResponseHeader(HttpRequest<Object> request, HttpResponse<Object> response, String name) {
        return response.getHeaders().getAll(name);
    }

    @Override
    @Nullable
    public String getFlavor(HttpRequest<Object> request) {
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
    public String getTarget(HttpRequest<Object> request) {
        String requestPath = request.getPath();
        String queryString = request.getUri().getRawQuery();
        if (StringUtils.isNotEmpty(queryString)) {
            return requestPath + '?' + queryString;
        }
        return requestPath;
    }

    @Override
    public String getRoute(HttpRequest<Object> request) {
        Optional<String> routeInfo = request.getAttribute(HttpAttributes.ROUTE_INFO)
            .filter(UriRouteInfo.class::isInstance)
            .map(ri -> (UriRouteInfo<?, ?>) ri)
            .map(UriRouteInfo::getUriMatchTemplate)
            .map(UriMatchTemplate::toPathString);
        return routeInfo.orElseGet(() ->
            request.getAttribute(HttpAttributes.URI_TEMPLATE)
                .map(Object::toString)
                .orElse(null)
        );
    }

    @Override
    public String getScheme(HttpRequest<Object> request) {
        return request.getUri().getScheme();
    }
}
