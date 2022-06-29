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
package io.micronaut.tracing.opentelemetry.idgenerator.xray;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.opentelemetry.contrib.awsxray.AwsXrayIdGenerator;
import io.opentelemetry.sdk.trace.IdGenerator;
import jakarta.inject.Singleton;

/**
 * Registers an instance of {@link AwsXrayIdGenerator#getInstance()} as a Singleton of type {@link IdGenerator}.
 * @author Sergio del Amo
 * @since 4.2.0
 */
@Requires(classes = AwsXrayIdGenerator.class)
@Factory
public class AwsXrayIdGeneratorFactory {

    /**
     * Creates a custom ID Generator for AWS XRay using {@link AwsXrayIdGenerator#getInstance()}.
     * @return An instance of {@link AwsXrayIdGenerator}.
     */
    @Replaces(IdGenerator.class)
    @Singleton
    IdGenerator idGenerator() {
        return AwsXrayIdGenerator.getInstance();
    }
}
