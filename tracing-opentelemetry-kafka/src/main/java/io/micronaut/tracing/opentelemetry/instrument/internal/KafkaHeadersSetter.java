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
package io.micronaut.tracing.opentelemetry.instrument.internal;

import java.nio.charset.StandardCharsets;

import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.context.propagation.TextMapSetter;

import org.apache.kafka.common.header.Headers;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
public enum KafkaHeadersSetter implements TextMapSetter<Headers> {
    INSTANCE;

    @Override
    public void set(@Nullable Headers headers, String key, String value) {
        if (headers == null) {
            return;
        }
        headers.remove(key).add(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
