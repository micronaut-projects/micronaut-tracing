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
package io.micronaut.tracing.opentelemetry.instrument.scheduling;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import io.opentelemetry.api.trace.Span;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scheduled method that calls itself and another scheduled method through the proxy within its run.
 */
@Requires(property = "spec.name", value = "ScheduledSpanSpec")
@Singleton
public class ReentrantScheduledJavaJob {

    final AtomicInteger runs = new AtomicInteger();
    final Set<String> runSpanIds = ConcurrentHashMap.newKeySet();
    final Set<String> nestedSpanIds = ConcurrentHashMap.newKeySet();
    final Set<String> otherSpanIds = ConcurrentHashMap.newKeySet();

    private final BeanProvider<ReentrantScheduledJavaJob> self;
    private final ThreadLocal<Boolean> nested = new ThreadLocal<>();

    public ReentrantScheduledJavaJob(BeanProvider<ReentrantScheduledJavaJob> self) {
        this.self = self;
    }

    @Scheduled(fixedDelay = "50ms")
    public void run() {
        String spanId = Span.current().getSpanContext().getSpanId();
        if (nested.get() != null) {
            nestedSpanIds.add(spanId);
            return;
        }
        runSpanIds.add(spanId);
        nested.set(Boolean.TRUE);
        try {
            self.get().run();
            self.get().other();
        } finally {
            nested.remove();
        }
        runs.incrementAndGet();
    }

    @Scheduled(fixedDelay = "1h", initialDelay = "1h")
    public void other() {
        otherSpanIds.add(Span.current().getSpanContext().getSpanId());
    }
}
