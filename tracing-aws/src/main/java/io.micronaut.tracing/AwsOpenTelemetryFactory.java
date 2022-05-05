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
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.AwsXrayIdGenerator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jakarta.inject.Singleton;

import java.util.Optional;


/**
 * Registers an OpenTelemetry bean.
 *
 * @author Nemanja Mikic
 */
@Factory
@Replaces(factory = DefaultOpenTelemetryFactory.class)
public class AwsOpenTelemetryFactory {

    private static final String DEFAULT_OTEL_COLLECTOR_ENDPOINT = "http://localhost:4317";

    /**
     * The OpenTelemetry bean configured with AwsOpenTelemetryConfiguration.
     * @param awsOpenTelemetryConfiguration the aws open-telemetry configuration
     * @return the OpenTelemetry
     */
    @Singleton
    @Primary
    OpenTelemetry defaultOpenTelemetryWithConfig(@NonNull AwsOpenTelemetryConfiguration awsOpenTelemetryConfiguration) {

        return OpenTelemetrySdk.builder()
            // This will enable your downstream requests to include the X-Ray trace header
            .setPropagators(
                ContextPropagators.create(
                    TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(), AwsXrayPropagator.getInstance())))

            // This provides basic configuration of a TracerProvider which generates X-Ray compliant IDs
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(
                            OtlpGrpcSpanExporter.builder()
                                .setEndpoint(
                                    Optional.ofNullable(awsOpenTelemetryConfiguration.getOtlpGrpcEndpoint())
                                    .filter(x -> !StringUtils.isEmpty(x))
                                    .orElse(DEFAULT_OTEL_COLLECTOR_ENDPOINT))
                                .build()
                        ).build())
                    .setIdGenerator(AwsXrayIdGenerator.getInstance())
                    .build())
            .build();
    }

    /**
     * The OpenTelemetry bean with default values.
     *
     * @return the OpenTelemetry
     */
    @Singleton
    OpenTelemetry defaultOpenTelemetry() {
        return OpenTelemetrySdk.builder()
            // This will enable your downstream requests to include the X-Ray trace header
            .setPropagators(
                ContextPropagators.create(
                    TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(), AwsXrayPropagator.getInstance())))

            // This provides basic configuration of a TracerProvider which generates X-Ray compliant IDs
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(
                            OtlpGrpcSpanExporter.builder()
                                .build()
                        ).build())
                    .setIdGenerator(AwsXrayIdGenerator.getInstance())
                    .build())
            .buildAndRegisterGlobal();
    }

}
