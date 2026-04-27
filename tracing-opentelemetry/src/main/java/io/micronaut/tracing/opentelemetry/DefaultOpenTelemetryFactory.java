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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.StringUtils;
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

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Registers an OpenTelemetry bean.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Factory
public class DefaultOpenTelemetryFactory {

    private static final String SERVICE_NAME_KEY = "otel.service.name";
    private static final String RESOURCE_ATTRIBUTES_KEY = "otel.resource.attributes";
    private static final String DEFAULT_TRACES_EXPORTER = "otel.traces.exporter";
    private static final String DEFAULT_METRICS_EXPORTER = "otel.metrics.exporter";
    private static final String DEFAULT_LOGS_EXPORTER = "otel.logs.exporter";
    private static final String REGISTER_GLOBAL = "otel.register.global";
    private static final String NONE = "none";

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

        Map<String, String> otel = resolveOtelProperties(environment);

        applicationConfiguration.getName().ifPresent(name -> otel.putIfAbsent(SERVICE_NAME_KEY, name));
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

    @Nullable
    private OpenTelemetry existingGlobalOpenTelemetry() {
        if (!GlobalOpenTelemetry.isSet()) {
            return null;
        }

        OpenTelemetry globalOpenTelemetry = GlobalOpenTelemetry.get();
        return globalOpenTelemetry.getTracerProvider() == TracerProvider.noop() ? null : globalOpenTelemetry;
    }

    private Map<String, String> resolveOtelProperties(Environment environment) {
        Map<String, String> otel = environment.getProperties("otel", StringConvention.RAW).entrySet().stream().collect(
            java.util.stream.Collectors.toMap(
                entry -> "otel." + normalizeOtelProperty(entry.getKey()),
                entry -> String.valueOf(entry.getValue()),
                (existing, replacement) -> existing
            )
        );
        environment.getProperty(RESOURCE_ATTRIBUTES_KEY, String.class).ifPresent(attributes ->
            otel.putIfAbsent(RESOURCE_ATTRIBUTES_KEY, attributes)
        );
        return otel;
    }

    private String normalizeOtelProperty(String property) {
        return property.toLowerCase(Locale.ENGLISH).replace('_', '.');
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
