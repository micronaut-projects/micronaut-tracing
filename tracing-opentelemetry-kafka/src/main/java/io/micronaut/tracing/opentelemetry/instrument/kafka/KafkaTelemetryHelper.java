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
import io.micronaut.context.annotation.Context;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Helper-class to access to kafkaTelemetry object from non-micronaut components.
 *
 * @since 4.6.0
 */
@Context
public final class KafkaTelemetryHelper {

    private static KafkaTelemetry kafkaTelemetry;

    @SuppressWarnings("rawtypes")
    KafkaTelemetryHelper(OpenTelemetry openTelemetry, KafkaTelemetryConfiguration kafkaTelemetryConfiguration,
                         ApplicationContext applicationContext) {
        Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters = applicationContext.getBeansOfType(KafkaTelemetryConsumerTracingFilter.class);
        Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters = applicationContext.getBeansOfType(KafkaTelemetryProducerTracingFilter.class);
        kafkaTelemetry = KafkaTelemetry.create(openTelemetry, kafkaTelemetryConfiguration, consumerTracingFilters, producerTracingFilters);
    }

    public static KafkaTelemetry getKafkaTelemetry() {
        return kafkaTelemetry;
    }
}
