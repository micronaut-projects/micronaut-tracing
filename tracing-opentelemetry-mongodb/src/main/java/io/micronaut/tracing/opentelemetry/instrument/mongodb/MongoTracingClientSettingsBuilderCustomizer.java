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
package io.micronaut.tracing.opentelemetry.instrument.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.event.CommandListener;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.MongoClientSettingsBuilderCustomizer;
import io.micronaut.core.annotation.Internal;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the OpenTelemetry MongoDB {@link CommandListener} to the {@link MongoClientSettings} of every
 * MongoDB client created by Micronaut MongoDB: the default client and the clients of named servers,
 * for both the synchronous and the reactive streams drivers.
 *
 * @since 8.4.0
 */
@Internal
@Singleton
final class MongoTracingClientSettingsBuilderCustomizer implements MongoClientSettingsBuilderCustomizer {

    private final MongoTelemetry telemetry;

    MongoTracingClientSettingsBuilderCustomizer(MongoTelemetryConfiguration configuration) {
        this.telemetry = configuration.build();
    }

    @Override
    public void customize(AbstractMongoConfiguration configuration, MongoClientSettings.Builder clientSettings) {
        // the configured seeds let the stable database conventions derive server.address and server.port
        List<ServerAddress> seeds = new ArrayList<>();
        clientSettings.applyToClusterSettings(cluster -> seeds.addAll(cluster.build().getHosts()));
        clientSettings.addCommandListener(telemetry.createCommandListener(seeds));
    }
}
