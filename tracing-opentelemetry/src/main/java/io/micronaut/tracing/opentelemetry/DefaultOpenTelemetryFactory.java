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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.runtime.ApplicationConfiguration;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registers an OpenTelemetry bean.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Factory
public class DefaultOpenTelemetryFactory {

    private static final String OTEL_PREFIX = "otel.";
    private static final String SERVICE_NAME_KEY = "otel.service.name";
    private static final String RESOURCE_ATTRIBUTES_KEY = "otel.resource.attributes";
    private static final String DEFAULT_TRACES_EXPORTER = "otel.traces.exporter";
    private static final String DEFAULT_METRICS_EXPORTER = "otel.metrics.exporter";
    private static final String DEFAULT_LOGS_EXPORTER = "otel.logs.exporter";
    private static final String REGISTER_GLOBAL = "otel.register.global";
    private static final String NONE = "none";
    private static final ArgumentConversionContext<Map<String, Object>> OTEL_PROPERTIES = ConversionContext.of(
        Argument.mapOf(String.class, Object.class).withAnnotationMetadata(mapFormatMetadata())
    );
    private static final List<String> MAP_PROPERTY_KEYS = Collections.unmodifiableList(Arrays.asList(
        RESOURCE_ATTRIBUTES_KEY,
        "otel.exporter.otlp.headers",
        "otel.exporter.otlp.traces.headers",
        "otel.exporter.otlp.metrics.headers",
        "otel.exporter.otlp.logs.headers"
    ));

    /**
     * The OpenTelemetry bean with default values.
     *
     * @param applicationConfiguration the {@link ApplicationConfiguration}
     * @param environment              the environment property resolver
     * @param idGenerator              the {@link IdGenerator}
     * @param spanProcessor            the {@link SpanProcessor}
     * @param resourceProvider         Resource Provider
     * @param sampler                  sampler
     * @param builderCustomizers       optional builder customizer beans
     * @return the OpenTelemetry bean with default values
     */
    @Singleton
    protected OpenTelemetry defaultOpenTelemetry(ApplicationConfiguration applicationConfiguration,
                                                 Environment environment,
                                                 @Nullable IdGenerator idGenerator,
                                                 @Nullable SpanProcessor spanProcessor,
                                                 @Nullable ResourceProvider resourceProvider,
                                                 @Nullable Sampler sampler,
                                                 Collection<OpenTelemetryBuilderCustomizer> builderCustomizers) {

        OpenTelemetry existingGlobalOpenTelemetry = existingGlobalOpenTelemetry();
        if (existingGlobalOpenTelemetry != null) {
            return existingGlobalOpenTelemetry;
        }

        Map<String, String> otel = resolveOpenTelemetryProperties(applicationConfiguration, resolveOtelProperties(environment));
        otel.putIfAbsent(DEFAULT_TRACES_EXPORTER, NONE);
        otel.putIfAbsent(DEFAULT_METRICS_EXPORTER, NONE);
        otel.putIfAbsent(DEFAULT_LOGS_EXPORTER, NONE);

        AutoConfiguredOpenTelemetrySdkBuilder sdk = AutoConfiguredOpenTelemetrySdk.builder();

        if (Boolean.parseBoolean(otel.getOrDefault(REGISTER_GLOBAL, StringUtils.FALSE)) && !GlobalOpenTelemetry.isSet()) {
            sdk.setResultAsGlobal();
        }

        sdk.addResourceCustomizer((resource, config) -> {
                if (resourceProvider != null) {
                    resource = resource.merge(resourceProvider.resource());
                }
                return resource;
            })
            .addPropertiesSupplier(() -> otel)
            .addTracerProviderCustomizer((tracerProviderBuilder, ignored) -> {
                    if (idGenerator != null) {
                        tracerProviderBuilder.setIdGenerator(idGenerator);
                    }
                    if (spanProcessor != null) {
                        tracerProviderBuilder.addSpanProcessor(spanProcessor);
                    }
                    if (sampler != null) {
                        tracerProviderBuilder.setSampler(sampler);
                    }

                    return tracerProviderBuilder;
                }
            );

        for (OpenTelemetryBuilderCustomizer customizer : builderCustomizers) {
            customizer.configure(sdk);
        }

        return sdk.build().getOpenTelemetrySdk();
    }

    static Map<String, String> resolveOpenTelemetryProperties(ApplicationConfiguration applicationConfiguration,
                                                              Map<String, String> otelConfig) {
        Map<String, String> otel = otelConfig.entrySet().stream().collect(Collectors.toMap(
            e -> e.getKey().startsWith(OTEL_PREFIX) ? e.getKey() : OTEL_PREFIX + e.getKey(),
            Map.Entry::getValue,
            (left, right) -> right,
            LinkedHashMap::new
        ));

        collapseMapProperties(otel);

        if (!hasServiceName(otel)) {
            applicationConfiguration.getName()
                .filter(name -> !isBlank(name))
                .ifPresent(name -> otel.put(SERVICE_NAME_KEY, name));
        }

        return otel;
    }

    private static void collapseMapProperties(Map<String, String> otel) {
        for (String mapPropertyKey : MAP_PROPERTY_KEYS) {
            Map<String, String> nestedValues = removeNestedValues(otel, mapPropertyKey);
            if (!nestedValues.isEmpty()) {
                String nestedConfig = toMapProperty(nestedValues);
                otel.compute(
                    mapPropertyKey,
                    (key, value) -> {
                        String normalizedValue = normalizeMapPropertyValue(value);
                        return isBlank(normalizedValue) ? nestedConfig : normalizedValue + "," + nestedConfig;
                    }
                );
            }
        }
    }

    private static String normalizeMapPropertyValue(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        while (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) == ',') {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static Map<String, String> removeNestedValues(Map<String, String> otel, String mapPropertyKey) {
        String prefix = mapPropertyKey + ".";
        Map<String, String> nestedValues = new LinkedHashMap<>();
        otel.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                nestedValues.put(entry.getKey().substring(prefix.length()), entry.getValue());
                return true;
            }
            return false;
        });
        return nestedValues;
    }

    private static String toMapProperty(Map<String, String> nestedValues) {
        return nestedValues.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(","));
    }

    private static boolean hasServiceName(Map<String, String> otel) {
        String serviceName = otel.get(SERVICE_NAME_KEY);
        if (!isBlank(serviceName)) {
            return true;
        }
        String resourceAttributes = otel.get(RESOURCE_ATTRIBUTES_KEY);
        return resourceAttributes != null && Stream.of(resourceAttributes.split(","))
            .map(String::trim)
            .anyMatch(DefaultOpenTelemetryFactory::hasNonBlankServiceNameAttribute);
    }

    private static boolean hasNonBlankServiceNameAttribute(String entry) {
        int separatorIndex = entry.indexOf('=');
        if (separatorIndex < 0) {
            return false;
        }
        String key = entry.substring(0, separatorIndex).trim();
        if (!"service.name".equals(key)) {
            return false;
        }
        String value = entry.substring(separatorIndex + 1).trim();
        return !isBlank(value);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    @Nullable
    private OpenTelemetry existingGlobalOpenTelemetry() {
        if (!GlobalOpenTelemetry.isSet()) {
            return null;
        }

        OpenTelemetry globalOpenTelemetry = GlobalOpenTelemetry.get();
        return globalOpenTelemetry.getTracerProvider() == TracerProvider.noop() ? null : globalOpenTelemetry;
    }

    private static Map<String, String> resolveOtelProperties(Environment environment) {
        Map<String, String> otel = environment.getProperty("otel", OTEL_PROPERTIES).orElse(Collections.emptyMap()).entrySet().stream().collect(
            Collectors.toMap(
                entry -> OTEL_PREFIX + normalizeOtelProperty(entry.getKey()),
                entry -> String.valueOf(entry.getValue()),
                (existing, replacement) -> existing,
                LinkedHashMap::new
            )
        );
        environment.getProperty(RESOURCE_ATTRIBUTES_KEY, String.class).ifPresent(attributes ->
            otel.putIfAbsent(RESOURCE_ATTRIBUTES_KEY, attributes)
        );
        return otel;
    }

    private static String normalizeOtelProperty(String property) {
        return property.toLowerCase(Locale.ENGLISH).replace('_', '.');
    }

    private static AnnotationMetadata mapFormatMetadata() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addAnnotation(MapFormat.class.getName(), Map.of(
            "transformation", MapFormat.MapTransformation.FLAT,
            "keyFormat", StringConvention.RAW
        ));
        return metadata;
    }

    /**
     * Reset OpenTelemetry, if it's running in test mode.
     *
     * @param environment The environment
     */
    @PreDestroy
    void resetForTest(Environment environment) {
        if (environment.getActiveNames().contains(Environment.TEST)) {
            GlobalOpenTelemetry.resetForTest();
        }
    }

}
