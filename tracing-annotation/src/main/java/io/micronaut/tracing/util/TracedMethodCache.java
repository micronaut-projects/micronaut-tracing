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
 * <p>The value of a method with evaluated expressions in its annotation metadata (e.g.
 * {@code @NewSpan("#{ #name }")}) depends on the arguments of each invocation, so it is computed for every
 * invocation instead.</p>
 *
 * @param <V> the cached value type
 * @since 8.4.0
 */
@Internal
public final class TracedMethodCache<V> {

    /**
     * Marks a method whose value is computed for every invocation.
     */
    private static final Object PER_INVOCATION = new Object();

    private final Function<MethodInvocationContext<?, ?>, V> resolver;
    private volatile Map<Object, Object> cache = new IdentityHashMap<>();

    /**
     * @param resolver computes the value of a method from an invocation context: the first one, or each one
     *                 when the method has evaluated expressions
     */
    public TracedMethodCache(Function<MethodInvocationContext<?, ?>, V> resolver) {
        this.resolver = resolver;
    }

    /**
     * Returns the value of the intercepted method, computing it on the first call, or on every call when the
     * method has evaluated expressions.
     *
     * @param context the invocation context
     * @return the value
     */
    @SuppressWarnings("unchecked")
    public V get(MethodInvocationContext<?, ?> context) {
        ExecutableMethod<?, ?> key = context.getExecutableMethod();
        Object value = cache.get(key);
        if (value == null) {
            value = resolve(key, context);
        }
        if (value == PER_INVOCATION) {
            // the annotation metadata of the context is bound to the arguments of this invocation
            return resolver.apply(context);
        }
        return (V) value;
    }

    private synchronized Object resolve(ExecutableMethod<?, ?> key, MethodInvocationContext<?, ?> context) {
        Map<Object, Object> current = cache;
        Object value = current.get(key);
        if (value == null) {
            value = key.getAnnotationMetadata().hasEvaluatedExpressions() ? PER_INVOCATION : resolver.apply(context);
            Map<Object, Object> copy = new IdentityHashMap<>(current);
            copy.put(key, value);
            cache = copy;
        }
        return value;
    }
}
