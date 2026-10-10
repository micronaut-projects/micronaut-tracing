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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteInfo;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;

import static io.micronaut.http.HttpVersion.HTTP_1_0;
import static io.micronaut.http.HttpVersion.HTTP_1_1;
import static io.micronaut.http.HttpVersion.HTTP_2_0;

@Internal
enum MicronautHttpServerAttributesGetter implements HttpServerAttributesGetter<HttpRequest<Object>, HttpResponse<Object>> {

    INSTANCE;

    private static final Map<HttpVersion, String> PROTOCOL_VERSION = Map.of(HTTP_1_0, "1.0", HTTP_1_1, "1.1", HTTP_2_0, "2.0");
    /**
     * Bounds the route cache, should routes be created dynamically.
     */
    private static final int MAX_CACHED_ROUTES = 1024;
    private static final Map<UriMatchTemplate, String> ROUTES = new ConcurrentHashMap<>();

    @Override
    public String getHttpRequestMethod(HttpRequest<Object> request) {
        return request.getMethodName();
    }

    @Override
    public List<String> getHttpRequestHeader(HttpRequest<Object> request, String name) {
        return request.getHeaders().getAll(name);
    }

    @Override
    public Integer getHttpResponseStatusCode(HttpRequest<Object> request, HttpResponse<Object> response, @Nullable Throwable error) {
        return response.code();
    }

    @Override
    public List<String> getHttpResponseHeader(HttpRequest<Object> request, HttpResponse<Object> response, String name) {
        return response.getHeaders().getAll(name);
    }

    @Override
    @Nullable
    public String getNetworkProtocolVersion(HttpRequest<Object> request, @Nullable HttpResponse<Object> response) {
        return PROTOCOL_VERSION.get(request.getHttpVersion());
    }

    @Override
    public String getUrlPath(HttpRequest<Object> request) {
        return request.getPath();
    }

    @Override
    @Nullable
    public String getUrlQuery(HttpRequest<Object> request) {
        return request.getUri().getRawQuery();
    }

    @Override
    @Nullable
    public String getHttpRoute(HttpRequest<Object> request) {
        if (RouteAttributes.getRouteInfo(request).orElse(null) instanceof UriRouteInfo<?, ?> routeInfo) {
            // the templates of the routes are fixed: compute the path of each one once. The cache is keyed by
            // the template (its hash code is the one of the template string) rather than the route, which
            // would retain the beans of the application context
            UriMatchTemplate template = routeInfo.getUriMatchTemplate();
            String route = ROUTES.get(template);
            if (route == null) {
                route = template.toPathString();
                if (ROUTES.size() < MAX_CACHED_ROUTES) {
                    ROUTES.putIfAbsent(template, route);
                }
            }
            return route;
        }
        return BasicHttpAttributes.getUriTemplate(request).orElse(null);
    }

    @Override
    public String getUrlScheme(HttpRequest<Object> request) {
        return request.getUri().getScheme();
    }
}
