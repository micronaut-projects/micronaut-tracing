/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.tracing.util.TracedMethod;
import io.micronaut.web.router.MethodBasedRouteInfo;
import io.micronaut.web.router.RouteAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.CodeAttributes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Adds the {@code code.function.name} attribute (the fully qualified name of the method, for example
 * {@code com.example.BookController.list}) to the HTTP server spans of the requests routed to a
 * controller method. The spans of the requests that match no route, of the error routes and of the
 * static resources have no code attributes.
 *
 * <p>The attribute value is computed once per route. The method name is the source name, without the
 * Kotlin mangling suffix of a function with an inline class (or {@code kotlin.Result}) in its signature.</p>
 *
 * <p>Disabled with {@code tracing.opentelemetry.http.server.code-attributes.enabled=false}, or replaced
 * with an {@link AttributesExtractor} bean annotated with
 * {@code @Replaces(HttpServerCodeAttributesExtractor.class)}.</p>
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@Singleton
@MicronautHttpServerTelemetryFactory.Server
@Requires(property = OpenTelemetryHttpServerCodeAttributesConfig.ENABLED, notEquals = StringUtils.FALSE)
public final class HttpServerCodeAttributesExtractor implements AttributesExtractor<HttpRequest<Object>, HttpResponse<Object>> {

    /**
     * The deprecated {@code code.namespace} attribute, replaced by {@code code.function.name}.
     */
    static final AttributeKey<String> CODE_NAMESPACE = AttributeKey.stringKey("code.namespace");

    /**
     * The deprecated {@code code.function} attribute, replaced by {@code code.function.name}.
     */
    static final AttributeKey<String> CODE_FUNCTION = AttributeKey.stringKey("code.function");

    /**
     * Bounds the cache, should routes be created dynamically.
     */
    private static final int MAX_CACHED_ROUTES = 1024;

    private final boolean legacyAttributes;
    /**
     * The code function of each route, keyed by the identity of the route. Copied on write, which only
     * happens the first time a route is requested.
     */
    private volatile Map<Object, CodeFunction> functions = new IdentityHashMap<>();

    /**
     * @param config the configuration, {@code null} for the defaults
     */
    public HttpServerCodeAttributesExtractor(@Nullable OpenTelemetryHttpServerCodeAttributesConfig config) {
        this.legacyAttributes = config != null && config.isLegacyAttributes();
    }

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, HttpRequest<Object> request) {
        if (RouteAttributes.getRouteInfo(request).orElse(null) instanceof MethodBasedRouteInfo<?, ?> route
            && !route.isErrorRoute()) {
            CodeFunction function = functions.get(route);
            if (function == null) {
                function = resolve(route);
            }
            if (function != null) {
                attributes.put(CodeAttributes.CODE_FUNCTION_NAME, function.name());
                if (legacyAttributes) {
                    attributes.put(CODE_NAMESPACE, function.namespace());
                    attributes.put(CODE_FUNCTION, function.function());
                }
            }
        }
    }

    @Override
    public void onEnd(AttributesBuilder attributes,
                      Context context,
                      HttpRequest<Object> request,
                      @Nullable HttpResponse<Object> response,
                      @Nullable Throwable error) {
        // the attributes are only added on start
    }

    @Nullable
    private synchronized CodeFunction resolve(MethodBasedRouteInfo<?, ?> route) {
        Map<Object, CodeFunction> current = functions;
        CodeFunction function = current.get(route);
        if (function != null) {
            return function;
        }
        function = CodeFunction.of(route);
        if (function != null && current.size() < MAX_CACHED_ROUTES) {
            Map<Object, CodeFunction> copy = new IdentityHashMap<>(current);
            copy.put(route, function);
            functions = copy;
        }
        return function;
    }

    /**
     * The code attributes of a route method.
     *
     * @param name the fully qualified name of the method
     * @param namespace the class name
     * @param function the method name
     */
    private record CodeFunction(String name, String namespace, String function) {

        @Nullable
        static CodeFunction of(MethodBasedRouteInfo<?, ?> route) {
            MethodExecutionHandle<?, ?> handle = route.getTargetMethod();
            if (handle == null) {
                return null;
            }
            ExecutableMethod<?, ?> method = handle.getExecutableMethod();
            String namespace = method.getDeclaringType().getName();
            String function = TracedMethod.methodName(method);
            return new CodeFunction(namespace + '.' + function, namespace, function);
        }
    }
}
