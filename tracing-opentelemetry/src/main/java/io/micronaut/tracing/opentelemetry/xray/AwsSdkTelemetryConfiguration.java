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
package io.micronaut.tracing.opentelemetry.xray;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;

/**
 * Configuration for OpenTelemetry AWS SDK instrumentation.
 *
 * @author Nemanja Mikic
 * @since 8.0.0
 */
@Internal
@ConfigurationProperties(AwsSdkTelemetryConfiguration.PREFIX)
public class AwsSdkTelemetryConfiguration {
    public static final String PREFIX = "otel.instrumentation.aws-sdk";

    private boolean experimentalSpanAttributes;
    private boolean experimentalUsePropagatorForMessaging;

    /**
     * @return Whether experimental AWS SDK span attributes should be captured.
     */
    public boolean isExperimentalSpanAttributes() {
        return experimentalSpanAttributes;
    }

    /**
     * @param experimentalSpanAttributes Whether experimental AWS SDK span attributes should be captured.
     */
    public void setExperimentalSpanAttributes(boolean experimentalSpanAttributes) {
        this.experimentalSpanAttributes = experimentalSpanAttributes;
    }

    /**
     * @return Whether messaging propagation should use the configured OpenTelemetry propagator.
     */
    public boolean isExperimentalUsePropagatorForMessaging() {
        return experimentalUsePropagatorForMessaging;
    }

    /**
     * @param experimentalUsePropagatorForMessaging Whether messaging propagation should use the configured OpenTelemetry propagator.
     */
    public void setExperimentalUsePropagatorForMessaging(boolean experimentalUsePropagatorForMessaging) {
        this.experimentalUsePropagatorForMessaging = experimentalUsePropagatorForMessaging;
    }
}
