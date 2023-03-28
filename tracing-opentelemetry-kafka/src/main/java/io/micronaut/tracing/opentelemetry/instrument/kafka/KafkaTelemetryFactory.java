/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.tracing.opentelemetry.instrument.kafka;

import java.util.Collection;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.opentelemetry.api.OpenTelemetry;

import jakarta.inject.Singleton;

/**
 * Opentelemetery Kafka tracing factory.
 *
 * @since 4.5.0
 */
@Factory
public final class KafkaTelemetryFactory {

    @Singleton
    public KafkaTelemetryConfig kafkaTelemetryConfig(OpenTelemetry openTelemetry, KafkaTelemetryProperties kafkaTelemetryProperties,
                                                     ApplicationContext applicationContext) {
        return new KafkaTelemetryConfig(openTelemetry, kafkaTelemetryProperties, applicationContext);
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    public KafkaTelemetry kafkaTelemetry(OpenTelemetry openTelemetry, KafkaTelemetryProperties kafkaTelemetryProperties,
                                         ApplicationContext applicationContext) {
        Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters = applicationContext.getBeansOfType(KafkaTelemetryConsumerTracingFilter.class);
        Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters = applicationContext.getBeansOfType(KafkaTelemetryProducerTracingFilter.class);
        return KafkaTelemetry.create(openTelemetry, kafkaTelemetryProperties, consumerTracingFilters, producerTracingFilters);
    }
}
