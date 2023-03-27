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
import java.util.ArrayList;
import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.context.propagation.TextMapGetter;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
public enum KafkaConsumerRecordGetter implements TextMapGetter<ConsumerRecord<?, ?>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
        List<String> headers = new ArrayList<>();
        for (Header header : carrier.headers()) {
            headers.add(header.key());
        }
        return headers;
    }

    @Nullable
    @Override
    public String get(@Nullable ConsumerRecord<?, ?> carrier, String key) {
        if (carrier == null) {
            return null;
        }
        Header header = carrier.headers().lastHeader(key);
        if (header == null) {
            return null;
        }
        byte[] value = header.value();
        if (value == null) {
            return null;
        }
        return new String(value, StandardCharsets.UTF_8);
    }
}
