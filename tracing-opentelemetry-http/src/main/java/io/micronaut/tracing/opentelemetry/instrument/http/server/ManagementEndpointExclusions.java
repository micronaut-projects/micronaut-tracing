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

import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.web.router.RouteBuilder;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Computes the exclusion patterns of the paths of the Micronaut management endpoints, added to the
 * {@code otel.exclusions} patterns of the HTTP server filter. The endpoints are the enabled
 * {@code @Endpoint} beans, so nothing is excluded without micronaut-management. Their paths are resolved as
 * the management routes are: the {@code endpoints.all.path} base path (default {@code /}), the
 * {@code endpoints.all.context.path}, the {@code endpoints.<id>.path} of an endpoint and the server context
 * path. The annotation is referenced by name, so micronaut-management is not needed on the classpath.
 *
 * @since 8.4.0
 */
@Internal
@Singleton
final class ManagementEndpointExclusions {

    private static final String ENDPOINT = "io.micronaut.management.endpoint.annotation.Endpoint";
    private static final String ENDPOINTS_PREFIX = "endpoints.";
    private static final String BASE_PATH = "endpoints.all.path";
    private static final String CONTEXT_PATH = "endpoints.all.context.path";
    private static final Pattern ENDPOINT_ID = Pattern.compile("[\\w-]+");
    private static final String SLASH = "/";

    private final List<String> patterns;

    /**
     * @param beanContext The bean context
     * @param properties  The property resolver
     * @param config      The HTTP server tracing configuration
     */
    ManagementEndpointExclusions(BeanContext beanContext,
                                 Environment properties,
                                 @Nullable OpenTelemetryHttpServerTracingConfig config) {
        boolean exclude = config == null ? OpenTelemetryHttpServerTracingConfig.DEFAULT_EXCLUDE_MANAGEMENT_ENDPOINTS : config.isExcludeManagementEndpoints();
        this.patterns = exclude ? patterns(beanContext, properties, config == null ? List.of() : config.getTracedManagementEndpoints()) : List.of();
    }

    /**
     * @return the regular expressions of the paths of the management endpoints to exclude
     */
    List<String> patterns() {
        return patterns;
    }

    private static List<String> patterns(BeanContext beanContext, Environment properties, List<String> traced) {
        var patterns = new ArrayList<String>();
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(ENDPOINT))) {
            String id = definition.stringValue(ENDPOINT).orElse(null);
            if (id != null && traced.contains(id)) {
                continue;
            }
            String path = id == null ? null : properties.get(ENDPOINTS_PREFIX + id + ".path", String.class)
                .filter(StringUtils::isNotEmpty)
                .map(p -> p.startsWith(SLASH) ? p.substring(1) : p)
                .orElse(null);
            if (path == null) {
                path = id == null || !ENDPOINT_ID.matcher(id).matches() ? NameUtils.hyphenate(definition.getName()) : id;
            }
            String uri = resolveUri(beanContext, properties, path);
            while (uri.length() > 1 && uri.endsWith(SLASH)) {
                uri = uri.substring(0, uri.length() - 1);
            }
            patterns.add(escape(uri) + "(?:/.*)?");
        }
        return patterns;
    }

    /**
     * Resolves the URI of an endpoint as the management route builder does.
     */
    private static String resolveUri(BeanContext beanContext, Environment properties, String id) {
        String basePath = properties.get(BASE_PATH, String.class).filter(StringUtils::isNotEmpty).orElse(SLASH);
        String contextPath = properties.get(CONTEXT_PATH, String.class).filter(StringUtils::isNotEmpty).orElse(null);
        String prefixed = StringUtils.prependUri(basePath, id);
        String path = prefixed.startsWith(SLASH) ? prefixed.substring(1) : prefixed;
        String uri;
        if (contextPath != null) {
            uri = NameUtils.hyphenate(StringUtils.prependUri(contextPath, path));
        } else {
            uri = beanContext.findBean(RouteBuilder.UriNamingStrategy.class)
                .map(naming -> naming.resolveUri(path))
                .orElse(path);
        }
        return uri.startsWith(SLASH) ? uri : SLASH + uri;
    }

    /**
     * Escapes the characters of a path that have a meaning in a regular expression. {@link Pattern#quote}
     * is not used: its {@code \Q} quotes prevent combining the patterns into one.
     */
    private static String escape(String path) {
        var escaped = new StringBuilder(path.length() + 8);
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if ("\\.[]{}()*+?^$|".indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
