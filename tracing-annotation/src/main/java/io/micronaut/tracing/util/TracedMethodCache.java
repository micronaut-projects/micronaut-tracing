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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A per-method cache of the tracing interceptors, keyed by the identity of the intercepted
 * {@link ExecutableMethod} (one instance per method of a bean definition). Lookups do not allocate nor
 * lock: the map is copied on write, which only happens the first time a method is intercepted.
 *
 * @param <V> the cached value type
 * @since 8.4.0
 */
@Internal
public final class TracedMethodCache<V> {

    private final Function<MethodInvocationContext<?, ?>, V> resolver;
    private volatile Map<Object, V> cache = new IdentityHashMap<>();

    /**
     * @param resolver computes the value of a method from its first invocation context
     */
    public TracedMethodCache(Function<MethodInvocationContext<?, ?>, V> resolver) {
        this.resolver = resolver;
    }

    /**
     * Returns the value of the intercepted method, computing it on the first call.
     *
     * @param context the invocation context
     * @return the value
     */
    public V get(MethodInvocationContext<?, ?> context) {
        Object key = context.getExecutableMethod();
        V value = cache.get(key);
        if (value == null) {
            value = resolve(key, context);
        }
        return value;
    }

    private synchronized V resolve(Object key, MethodInvocationContext<?, ?> context) {
        Map<Object, V> current = cache;
        V value = current.get(key);
        if (value == null) {
            value = resolver.apply(context);
            Map<Object, V> copy = new IdentityHashMap<>(current);
            copy.put(key, value);
            cache = copy;
        }
        return value;
    }
}
