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
package io.micronaut.tracing.opentelemetry.instrument.jms;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.jms.JMSException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Reads and writes the propagation fields as JMS message properties.
 *
 * <p>JMS property names must be valid Java identifiers, so a {@code -} in a propagation field name
 * (for example {@code X-B3-TraceId}) is stored as {@value #DASH}, as the OpenTelemetry Java agent
 * does.</p>
 *
 * @since 8.4.0
 */
@Internal
enum JmsMessagePropertyAccessor implements TextMapSetter<JmsRequest>, TextMapGetter<JmsRequest> {

    INSTANCE;

    /**
     * The replacement of {@code -} in property names.
     */
    static final String DASH = "__dash__";

    @Override
    public void set(@Nullable JmsRequest carrier, String key, String value) {
        if (carrier == null) {
            return;
        }
        try {
            carrier.message().setStringProperty(propertyName(key), value);
        } catch (JMSException | RuntimeException e) {
            // for example a read-only message being re-sent: the message is sent without the trace context
        }
    }

    @Override
    public Iterable<String> keys(JmsRequest carrier) {
        try {
            Enumeration<?> names = carrier.message().getPropertyNames();
            if (names == null) {
                return Collections.emptyList();
            }
            List<String> keys = new ArrayList<>();
            while (names.hasMoreElements()) {
                Object name = names.nextElement();
                if (name instanceof String s) {
                    keys.add(s.replace(DASH, "-"));
                }
            }
            return keys;
        } catch (JMSException | RuntimeException e) {
            return Collections.emptyList();
        }
    }

    @Override
    @Nullable
    public String get(@Nullable JmsRequest carrier, String key) {
        if (carrier == null) {
            return null;
        }
        try {
            Object value = carrier.message().getObjectProperty(propertyName(key));
            return value == null ? null : value.toString();
        } catch (JMSException | RuntimeException e) {
            return null;
        }
    }

    static String propertyName(String key) {
        return key.replace("-", DASH);
    }
}
