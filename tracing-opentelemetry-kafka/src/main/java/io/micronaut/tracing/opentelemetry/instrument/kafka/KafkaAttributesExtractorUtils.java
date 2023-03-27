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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.opentelemetry.api.common.AttributesBuilder;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/**
 *
 * @since 4.6.0
 */
public final class KafkaAttributesExtractorUtils {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String DOT = ".";

    private KafkaAttributesExtractorUtils() {
    }

    /**
     * Add message headers as span attributes.
     *
     * @param kafkaTelemetryProperties kafkaTelemetryProperties
     * @param attributes attributes builder
     * @param headers kafka message headers
     */
    static void putAttributes(KafkaTelemetryProperties kafkaTelemetryProperties, AttributesBuilder attributes, Headers headers) {
        Set<String> capturedHeaders = kafkaTelemetryProperties.getCapturedHeaders();
        if (capturedHeaders.contains(KafkaTelemetryProperties.ALL_HEADERS)) {
            Map<String, Integer> counterMap = new HashMap<>();
            for (Header header : headers) {
                processHeader(kafkaTelemetryProperties, attributes, header, counterMap);
            }
        } else {
            applyHeaders(kafkaTelemetryProperties, attributes, headers);
        }
    }

    private static void applyHeaders(KafkaTelemetryProperties kafkaTelemetryProperties, AttributesBuilder attributes, Headers headers) {
        Set<String> capturedHeaders = kafkaTelemetryProperties.getCapturedHeaders();
        if (kafkaTelemetryProperties.isHeadersAsLists()) {
            applyHeadersAsList(kafkaTelemetryProperties, attributes, headers, capturedHeaders);
        } else {
            Map<String, Integer> counterMap = new HashMap<>();
            for (String headerName : capturedHeaders) {
                Header header = headers.lastHeader(headerName);
                if (header != null) {
                    processHeader(kafkaTelemetryProperties, attributes, header, counterMap);
                }
            }
        }
    }

    private static void applyHeadersAsList(KafkaTelemetryProperties kafkaTelemetryProperties, AttributesBuilder attributes, Headers headers, Set<String> capturedHeaders) {
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
                String spanAttrName;
                if (kafkaTelemetryProperties.isAttributeWithPrefix()) {
                    spanAttrName = kafkaTelemetryProperties.getAttributePrefix() + headerName;
                } else {
                    spanAttrName = headerName;
                }
                attributes.put(spanAttrName, values.toArray(EMPTY_STRING_ARRAY));
            }
        }
    }

    private static void processHeader(KafkaTelemetryProperties kafkaTelemetryProperties, AttributesBuilder attributes, Header header, Map<String, Integer> counterMap) {
        String headerValue = header.value() != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        if (headerValue == null) {
            return;
        }
        String spanAttrName;
        if (kafkaTelemetryProperties.isAttributeWithPrefix()) {
            spanAttrName = kafkaTelemetryProperties.getAttributePrefix() + header.key();
        } else {
            spanAttrName = header.key();
        }
        Integer counter = counterMap.getOrDefault(spanAttrName, 0);
        if (counter > 0) {
            spanAttrName += DOT + counter;
        }
        counter++;
        counterMap.put(spanAttrName, counter);
        attributes.put(spanAttrName, headerValue);
    }

}
