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
package io.micronaut.tracing.opentelemetry;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.core.value.PropertyResolver;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenTelemetry {@link ConfigProperties} that read the Micronaut environment first and fall back to the
 * properties OpenTelemetry resolved itself (system properties, environment variables and the defaults
 * supplied by {@link DefaultOpenTelemetryFactory}).
 *
 * <p>Map properties accept both shapes: the OpenTelemetry string format ({@code key=value,key2=value2})
 * and nested configuration ({@code otel.exporter.otlp.headers.Authorization: ...}), which is read with
 * {@link MapFormat} so keys keep their case and dots. List properties accept a comma-separated string
 * or a list.</p>
 *
 * @since 8.4.0
 */
@Internal
final class EnvironmentConfigProperties implements ConfigProperties {

    private static final String SERVICE_NAME = "otel.service.name";
    private static final String RESOURCE_ATTRIBUTES = "otel.resource.attributes";
    private static final String SERVICE_NAME_ATTRIBUTE = "service.name";
    private static final ArgumentConversionContext<Map<String, String>> FLAT_MAP = flatMapContext();
    private static final Argument<List<String>> STRING_LIST = Argument.listOf(String.class);

    private final PropertyResolver environment;
    private final ConfigProperties fallback;
    @Nullable
    private final String applicationName;

    EnvironmentConfigProperties(PropertyResolver environment, ConfigProperties fallback, @Nullable String applicationName) {
        this.environment = environment;
        this.fallback = fallback;
        this.applicationName = applicationName;
    }

    @Override
    @Nullable
    public String getString(String name) {
        String value = environment.getProperty(name, String.class).filter(StringUtils::isNotEmpty).orElse(null);
        if (value == null) {
            value = fallback.getString(name);
        }
        if (SERVICE_NAME.equals(name) && isBlank(value) && !hasServiceNameAttribute()) {
            return applicationName;
        }
        return value;
    }

    @Override
    @Nullable
    public Boolean getBoolean(String name) {
        return environment.getProperty(name, Boolean.class).orElseGet(() -> fallback.getBoolean(name));
    }

    @Override
    @Nullable
    public Integer getInt(String name) {
        return environment.getProperty(name, Integer.class).orElseGet(() -> fallback.getInt(name));
    }

    @Override
    @Nullable
    public Long getLong(String name) {
        return environment.getProperty(name, Long.class).orElseGet(() -> fallback.getLong(name));
    }

    @Override
    @Nullable
    public Double getDouble(String name) {
        return environment.getProperty(name, Double.class).orElseGet(() -> fallback.getDouble(name));
    }

    @Override
    @Nullable
    public Duration getDuration(String name) {
        // OpenTelemetry durations without a unit are milliseconds, so parse them the OpenTelemetry way
        return environment.getProperty(name, String.class)
            .map(value -> DefaultConfigProperties.createFromMap(Collections.singletonMap(name, value)).getDuration(name))
            .orElseGet(() -> fallback.getDuration(name));
    }

    @Override
    public List<String> getList(String name) {
        return environment.getProperty(name, STRING_LIST)
            .filter(list -> !list.isEmpty())
            .orElseGet(() -> fallback.getList(name));
    }

    @Override
    public Map<String, String> getMap(String name) {
        // nested configuration or a YAML map, for example otel.exporter.otlp.headers.Authorization
        Map<String, String> nested = environment.getProperty(name, FLAT_MAP).orElse(null);
        if (nested != null && !nested.isEmpty()) {
            return nested;
        }
        // the OpenTelemetry string format, for example otel.exporter.otlp.headers=Authorization=Bearer token
        String value = environment.getProperty(name, String.class).orElse(null);
        if (value == null) {
            return fallback.getMap(name);
        }
        Map<String, String> map = new LinkedHashMap<>(
            DefaultConfigProperties.createFromMap(Collections.singletonMap(name, value)).getMap(name)
        );
        // both shapes at once: nested keys are added to, and override, the string entries
        environment.getProperties(name, StringConvention.RAW)
            .forEach((key, nestedValue) -> map.put(key, String.valueOf(nestedValue)));
        return map;
    }

    private boolean hasServiceNameAttribute() {
        return !isBlank(getMap(RESOURCE_ATTRIBUTES).get(SERVICE_NAME_ATTRIBUTE));
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static ArgumentConversionContext<Map<String, String>> flatMapContext() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addAnnotation(MapFormat.class.getName(), Map.of(
            "transformation", MapFormat.MapTransformation.FLAT,
            "keyFormat", StringConvention.RAW
        ));
        return ConversionContext.of(Argument.mapOf(String.class, String.class)).with(metadata);
    }
}
