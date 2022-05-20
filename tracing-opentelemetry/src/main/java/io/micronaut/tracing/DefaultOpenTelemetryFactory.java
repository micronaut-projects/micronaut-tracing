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
package io.micronaut.tracing;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.micronaut.runtime.ApplicationConfiguration.APPLICATION_NAME;

/**
 * Registers an OpenTelemetry bean.
 *
 * @author Nemanja Mikic
 */
@Factory
public class DefaultOpenTelemetryFactory {

    private static final String SERVICE_NAME_KEY = "otel.service.name";
    private static final String DEFAULT_TRACES_EXPORTER = "otel.traces.exporter";
    private static final String DEFAULT_METRICS_EXPORTER = "otel.metrics.exporter";
    private static final String DEFAULT_LOGS_EXPORTER = "otel.logs.exporter";
    private static final String NONE = "none";

    /**
     * The OpenTelemetry bean with default values.
     * @param applicationName the application name from configuration
     * @param otelProperties the configuration properties for the opentelemetry autoconfigure
     * @return the OpenTelemetry
     */
    @Singleton
    @Primary
    protected OpenTelemetry defaultOpenTelemetry(@Property(name = APPLICATION_NAME) String applicationName,
                                                 @Property(name = "otel") Properties otelProperties) {

        Map<String, String> otel = otelProperties.entrySet().stream().collect(
            Collectors.toMap(
                e -> "otel." + e.getKey(),
                e -> String.valueOf(e.getValue()),
                (prev, next) -> next, HashMap::new
        ));

        if (!otel.containsKey(SERVICE_NAME_KEY)) {
            otel.put(SERVICE_NAME_KEY, applicationName);
        }
        if (!otel.containsKey(DEFAULT_TRACES_EXPORTER)) {
            otel.put(DEFAULT_TRACES_EXPORTER, NONE);
        }
        if (!otel.containsKey(DEFAULT_METRICS_EXPORTER)) {
            otel.put(DEFAULT_METRICS_EXPORTER, NONE);
        }
        if (!otel.containsKey(DEFAULT_LOGS_EXPORTER)) {
            otel.put(DEFAULT_LOGS_EXPORTER, NONE);
        }
        return AutoConfiguredOpenTelemetrySdk.builder()
            .setResultAsGlobal(true)
            .addPropertiesSupplier(() -> otel)
            .build().getOpenTelemetrySdk();
    }
}
