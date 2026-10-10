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
package io.micronaut.tracing.opentelemetry.instrument.redis;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.Tracing;
import io.micronaut.configuration.lettuce.AbstractRedisConfiguration;
import io.micronaut.configuration.lettuce.ClientResourcesMutator;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

/**
 * Registers the OpenTelemetry Lettuce {@link Tracing} with the {@link ClientResources} of every
 * Redis client created by Micronaut Redis: the default, cluster and named server clients.
 *
 * @since 8.4.0
 */
@Internal
@Singleton
final class LettuceTracingClientResourcesMutator implements ClientResourcesMutator {

    private final Tracing tracing;

    LettuceTracingClientResourcesMutator(LettuceTelemetryConfiguration configuration) {
        this.tracing = configuration.build().createTracing();
    }

    @Override
    public void mutate(ClientResources.Builder builder, AbstractRedisConfiguration config) {
        builder.tracing(tracing);
    }
}
