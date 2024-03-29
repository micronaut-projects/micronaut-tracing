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

import java.util.Collections;
import java.util.Set;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

/**
 * Configuration properties for KafkaTelemetry.
 *
 * @since 4.5.0
 */
@Requires(property = KafkaTelemetryConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@ConfigurationProperties(KafkaTelemetryConfiguration.PREFIX)
public class KafkaTelemetryConfiguration {

    /**
     * The default prefix used for Kafka Telemetry configuration.
     */
    public static final String PREFIX = "otel.instrumentation.kafka";
    /**
     * Value to capture all headers as span attributes.
     */
    public static final String ALL_HEADERS = "*";
    /**
     * Default prefix value for span attributes.
     */
    private static final String DEFAULT_ATTR_PREFIX = "messaging.header.";
    /**
     * If you want to set headers as lists, set "true".
     */
    private boolean headersAsLists;
    /**
     * Enable or disable adding the span attribute prefix to header names.
     */
    private boolean attributeWithPrefix;
    /**
     * Span attributes prefix for header names.
     * <p>
     * Default: messaging.header.
     */
    private String attributePrefix = DEFAULT_ATTR_PREFIX;
    /**
     * List of headers, which you want to add as span attributes. By default, all headers
     * will be added as span attributes. If you don't want to set any headers as attributes,
     * just set it to `null` or an empty string.
     */
    private Set<String> capturedHeaders = Collections.singleton(ALL_HEADERS);
    /**
     * List of kafka topics in which messages need to trace.
     */
    private Set<String> includedTopics = Collections.emptySet();
    /**
     * List of kafka topics in which messages don't need to trace.
     * <p>
     * Important! If you set includedTopics property, this property will be ignored.
     */
    private Set<String> excludedTopics = Collections.emptySet();

    /**
     * Getter for headersAsLists flag.
     *
     * @return headersAsLists
     */
    public boolean isHeadersAsLists() {
        return headersAsLists;
    }

    /**
     * Setter for headersAsLists flag.
     *
     * @param headersAsLists flag
     */
    public void setHeadersAsLists(boolean headersAsLists) {
        this.headersAsLists = headersAsLists;
    }

    /**
     * Getter for captured headers set.
     *
     * @return capturedHeaders
     */
    public Set<String> getCapturedHeaders() {
        return capturedHeaders;
    }

    /**
     * Setter for captured headers set.
     *
     * @param capturedHeaders capturedHeaders set
     */
    public void setCapturedHeaders(Set<String> capturedHeaders) {
        this.capturedHeaders = capturedHeaders;
    }

    /**
     * Getter for includedTopics.
     *
     * @return includedTopics
     */
    public Set<String> getIncludedTopics() {
        return includedTopics;
    }

    /**
     * Setter for included topics set.
     *
     * @param includedTopics includedTopics set
     */
    public void setIncludedTopics(Set<String> includedTopics) {
        this.includedTopics = includedTopics;
    }

    /**
     * Getter for excludedTopics.
     *
     * @return excludedTopics
     */
    public Set<String> getExcludedTopics() {
        return excludedTopics;
    }

    /**
     * Setter for excluded topics set.
     *
     * @param excludedTopics excludedTopics set
     */
    public void setExcludedTopics(Set<String> excludedTopics) {
        this.excludedTopics = excludedTopics;
    }

    /**
     * Getter for captured headers set.
     *
     * @return attributeWithPrefix
     */
    public boolean isAttributeWithPrefix() {
        return attributeWithPrefix;
    }

    /**
     * Setter for attributeWithPrefix flag.
     *
     * @param attributeWithPrefix attributeWithPrefix flag
     */
    public void setAttributeWithPrefix(boolean attributeWithPrefix) {
        this.attributeWithPrefix = attributeWithPrefix;
    }

    /**
     * Getter for attributePrefix.
     *
     * @return attributePrefix
     */
    public String getAttributePrefix() {
        return attributePrefix;
    }

    /**
     * Setter for attributePrefix.
     *
     * @param attributePrefix attributePrefix set
     */
    public void setAttributePrefix(String attributePrefix) {
        this.attributePrefix = attributePrefix;
    }
}
