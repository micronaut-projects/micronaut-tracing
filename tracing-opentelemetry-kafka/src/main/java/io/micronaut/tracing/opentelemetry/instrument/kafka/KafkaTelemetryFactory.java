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
package io.micronaut.tracing.opentelemetry.instrument.kafka;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.micronaut.context.annotation.Factory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.kafkaclients.KafkaTelemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import jakarta.inject.Singleton;

/**
 * Opentelemetery Kafka tracing factory.
 *
 * @since 5.0.0
 */
@Factory
public class KafkaTelemetryFactory {

    private static final String ATTR_PREFIX = "messaging.header.";

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String DOT = ".";

    /**
     * Create the KafkaTelemetry bean.
     *
     * @param openTelemetry opentelemetry bean
     * @param kafkaTelemetryConfiguration kafkaTelemetryConfiguration bean
     *
     * @return The {@link KafkaTelemetry} bean
     */
    @Singleton
    public KafkaTelemetry kafkaTelemetry(OpenTelemetry openTelemetry, KafkaTelemetryConfiguration kafkaTelemetryConfiguration) {
        return KafkaTelemetry.builder(openTelemetry)
            .addConsumerAttributesExtractors(new AttributesExtractor<>() {
                @Override
                public void onStart(AttributesBuilder attributes, Context parentContext, ConsumerRecord<?, ?> consumerRecord) {
                    putAttributes(attributes, consumerRecord.headers(), kafkaTelemetryConfiguration);
                }

                @Override
                public void onEnd(AttributesBuilder attributes, Context context, ConsumerRecord<?, ?> consumerRecord, Void unused, Throwable error) {
                    // do notting in the end
                }
            })
            .addProducerAttributesExtractors(new AttributesExtractor<>() {
                @Override
                public void onStart(AttributesBuilder attributes, Context parentContext, ProducerRecord<?, ?> producerRecord) {
                    putAttributes(attributes, producerRecord.headers(), kafkaTelemetryConfiguration);
                }

                @Override
                public void onEnd(AttributesBuilder attributes, Context context, ProducerRecord<?, ?> producerRecord, Void unused, Throwable error) {
                    // do notting in the end
                }
            })
            .build();
    }

    /**
     * Add message headers as span attributes.
     *
     * @param attributes attributes builder
     * @param headers kafka message headers
     * @param kafkaTelemetryConfiguration kafka telemtry configuration
     */
    void putAttributes(AttributesBuilder attributes, Headers headers, KafkaTelemetryConfiguration kafkaTelemetryConfiguration) {
        Set<String> capturedHeaders = kafkaTelemetryConfiguration.getCapturedHeaders();
        if (capturedHeaders.contains(KafkaTelemetryConfiguration.ALL_HEADERS)) {
            Map<String, Integer> counterMap = new HashMap<>();
            for (Header header : headers) {
                processHeader(attributes, header, counterMap);
            }
        } else {
            applyHeaders(attributes, headers, kafkaTelemetryConfiguration);
        }
    }

    private void applyHeaders(AttributesBuilder attributes, Headers headers, KafkaTelemetryConfiguration kafkaTelemetryConfiguration) {
        Set<String> capturedHeaders = kafkaTelemetryConfiguration.getCapturedHeaders();
        if (kafkaTelemetryConfiguration.isHeadersAsLists()) {
            applyHeadersAsList(attributes, headers, capturedHeaders);
        } else {
            Map<String, Integer> counterMap = new HashMap<>();
            for (String headerName : capturedHeaders) {
                Header header = headers.lastHeader(headerName);
                if (header != null) {
                    processHeader(attributes, header, counterMap);
                }
            }
        }
    }

    private void applyHeadersAsList(AttributesBuilder attributes, Headers headers, Set<String> capturedHeaders) {
        for (String headerName : capturedHeaders) {
            List<String> values = null;
            Iterable<Header> headersByName = headers.headers(headerName);
            for (Header header : headersByName) {
                if (values == null) {
                    values = new ArrayList<>();
                }
                values.add(new String(header.value(), StandardCharsets.UTF_8));
            }
            if (values != null) {
                attributes.put(ATTR_PREFIX + headerName, values.toArray(EMPTY_STRING_ARRAY));
            }
        }
    }

    private void processHeader(AttributesBuilder attributes, Header header, Map<String, Integer> counterMap) {
        String headerValue = header.value() != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        if (headerValue == null) {
            return;
        }
        String key = ATTR_PREFIX + header.key();
        Integer counter = counterMap.getOrDefault(key, 0);
        if (counter > 0) {
            key += DOT + counter;
        }
        counter++;
        counterMap.put(key, counter);
        attributes.put(key, headerValue);
    }
}