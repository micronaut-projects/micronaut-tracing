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
package io.micronaut.tracing.util;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Shared MongoDB container for the module tests.
 */
public final class Mongo {
    private static GenericContainer<?> container;

    private Mongo() {
    }

    public static synchronized String getUri() {
        if (container == null) {
            container = new GenericContainer<>("mongo:7")
                .withExposedPorts(27017)
                .waitingFor(Wait.forLogMessage("(?i).*waiting for connections.*", 1));
            container.start();
        }
        return "mongodb://%s:%d".formatted(container.getHost(), container.getMappedPort(27017));
    }
}
