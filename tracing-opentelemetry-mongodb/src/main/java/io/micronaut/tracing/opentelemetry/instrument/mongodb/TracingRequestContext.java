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

import com.mongodb.RequestContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A MongoDB {@link RequestContext} that carries the OpenTelemetry {@link Context} of the caller
 * that issued an operation, so that the command spans can be parented to it.
 *
 * @since 8.4.0
 */
@Internal
final class TracingRequestContext implements RequestContext {

    private static final Object OTEL_CONTEXT_KEY = Context.class;

    private final Map<Object, Object> values = new ConcurrentHashMap<>();

    TracingRequestContext(Context context) {
        values.put(OTEL_CONTEXT_KEY, context);
    }

    /**
     * @param requestContext The request context of a command, possibly {@code null}
     * @return The OpenTelemetry context it carries, or {@code null}
     */
    static @Nullable Context otelContext(@Nullable RequestContext requestContext) {
        if (requestContext == null) {
            return null;
        }
        Object context = requestContext.get(OTEL_CONTEXT_KEY);
        return context instanceof Context otelContext ? otelContext : null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Object key) {
        return (T) values.get(key);
    }

    @Override
    public boolean hasKey(Object key) {
        return values.containsKey(key);
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public void put(Object key, Object value) {
        values.put(key, value);
    }

    @Override
    public void delete(Object key) {
        values.remove(key);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public Stream<Map.Entry<Object, Object>> stream() {
        return values.entrySet().stream();
    }
}
