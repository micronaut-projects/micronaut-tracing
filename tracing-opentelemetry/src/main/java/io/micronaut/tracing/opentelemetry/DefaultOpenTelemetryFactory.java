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
package io.micronaut.tracing.opentelemetry;

import java.util.Map;
import java.util.stream.Collectors;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.ApplicationConfiguration;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SpanProcessor;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import static io.micronaut.core.convert.format.MapFormat.MapTransformation.FLAT;

/**
 * Registers an OpenTelemetry bean.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Factory
public class DefaultOpenTelemetryFactory {

    private static final String SERVICE_NAME_KEY = "otel.service.name";
    private static final String DEFAULT_TRACES_EXPORTER = "otel.traces.exporter";
    private static final String DEFAULT_METRICS_EXPORTER = "otel.metrics.exporter";
    private static final String DEFAULT_LOGS_EXPORTER = "otel.logs.exporter";
    private static final String REGISTER_GLOBAL = "otel.register.global";
    private static final String NONE = "none";

    /**
     * The OpenTelemetry bean with default values.
     *
     * @param applicationConfiguration the {@link ApplicationConfiguration}
     * @param otelConfig the configuration values for the opentelemetry autoconfigure
     * @param idGenerator the {@link IdGenerator}
     * @param spanProcessor the {@link SpanProcessor}
     * @param resourceProvider Resource Provider
     *
     * @return the OpenTelemetry bean with default values
     */
    @Singleton
    protected OpenTelemetry defaultOpenTelemetry(ApplicationConfiguration applicationConfiguration,
                                                 @Property(name = "otel") @MapFormat(transformation = FLAT) Map<String, String> otelConfig,
                                                 @Nullable IdGenerator idGenerator,
                                                 @Nullable SpanProcessor spanProcessor,
                                                 @Nullable ResourceProvider resourceProvider,
                                                 @Nullable Sampler sampler) {

        Map<String, String> otel = otelConfig.entrySet().stream().collect(Collectors.toMap(
            e -> "otel." + e.getKey(),
            Map.Entry::getValue
        ));

        otel.putIfAbsent(SERVICE_NAME_KEY, applicationConfiguration.getName().orElse(""));
        otel.putIfAbsent(DEFAULT_TRACES_EXPORTER, NONE);
        otel.putIfAbsent(DEFAULT_METRICS_EXPORTER, NONE);
        otel.putIfAbsent(DEFAULT_LOGS_EXPORTER, NONE);

        AutoConfiguredOpenTelemetrySdkBuilder sdk = AutoConfiguredOpenTelemetrySdk.builder();

        if (Boolean.parseBoolean(otel.getOrDefault(REGISTER_GLOBAL, StringUtils.FALSE))) {
            sdk.setResultAsGlobal();
        }

        sdk.addPropertiesSupplier(() -> otel)
            .addTracerProviderCustomizer((tracerProviderBuilder, ignored) -> {
                    if (idGenerator != null) {
                        tracerProviderBuilder.setIdGenerator(idGenerator);
                    }
                    if (spanProcessor != null) {
                        tracerProviderBuilder.addSpanProcessor(spanProcessor);
                    }
                    if (resourceProvider != null) {
                        tracerProviderBuilder.setResource(resourceProvider.resource());
                    }
                    if (sampler != null) {
                        tracerProviderBuilder.setSampler(sampler);
                    }

                    return tracerProviderBuilder;
                }
            );

        return sdk.build().getOpenTelemetrySdk();
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
