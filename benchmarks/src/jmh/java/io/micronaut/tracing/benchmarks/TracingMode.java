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
package io.micronaut.tracing.benchmarks;

import java.util.HashMap;
import java.util.Map;

/**
 * The tracing configurations compared by the benchmarks. The {@code @Param} values of the benchmarks are
 * the {@link #name()}s of these constants.
 */
public enum TracingMode {

    /**
     * A: tracing disabled ({@code micronaut.otel.enabled=false}), so no OpenTelemetry bean, filter or
     * interceptor is created. This is the baseline.
     */
    NONE,

    /**
     * B: OpenTelemetry with the Micronaut defaults: every exporter {@code none}, default sampler
     * ({@code parentbased_always_on}), so every span is recorded and then dropped.
     */
    OTEL,

    /**
     * C: B plus a {@code BatchSpanProcessor} with a no-op exporter, i.e. 100% sampling and a realistic
     * span pipeline without any I/O.
     */
    OTEL_NOOP_EXPORTER,

    /**
     * D: B with a {@code parentbased_traceidratio} sampler at 10%.
     */
    OTEL_RATIO_10;

    /**
     * @return the application properties for this mode
     */
    public Map<String, Object> properties() {
        Map<String, Object> props = new HashMap<>();
        props.put("micronaut.server.port", -1);
        props.put("micronaut.application.name", "tracing-benchmarks");
        switch (this) {
            case NONE -> props.put("micronaut.otel.enabled", false);
            case OTEL -> { }
            case OTEL_NOOP_EXPORTER -> props.put(NoopSpanProcessorFactory.PROPERTY, true);
            case OTEL_RATIO_10 -> {
                props.put("otel.traces.sampler", "parentbased_traceidratio");
                props.put("otel.traces.sampler.arg", "0.1");
            }
            default -> throw new IllegalStateException("Unknown mode " + this);
        }
        return props;
    }
}
