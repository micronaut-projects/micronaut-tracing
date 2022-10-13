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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.runtime.ApplicationConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.inject.Singleton;

/**
 * Creates a default NoopTracer if no other tracer is present.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Factory
public class DefaultTelemetryTracer {

    private final String applicationName;

    DefaultTelemetryTracer(ApplicationConfiguration applicationConfiguration) {
        this.applicationName = applicationConfiguration.getName().orElse("");
    }

    /**
     * Creates a default {@link Tracer} if no other {@code Tracer} is present.
     * @param openTelemetry the openTelemetry
     * @return no-op {@code Tracer}
     */
    @Singleton
    @Primary
    @Requires(missingBeans = Tracer.class)
    Tracer defaultTracer(@NonNull OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(applicationName);
    }

}
