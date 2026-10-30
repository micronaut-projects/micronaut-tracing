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

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Async;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * A scheduled method that runs asynchronously: its stage fails after it was dispatched.
 */
@Requires(property = "spec.name", value = "ScheduledSpanSpec")
@Singleton
public class AsyncScheduledJavaJob {

    static final long DELAY_MILLIS = 200;

    @Scheduled(fixedDelay = "1h", initialDelay = "10ms")
    @Async
    public CompletionStage<Void> run() {
        return CompletableFuture.runAsync(() -> {
            throw new IllegalStateException("late failure");
        }, CompletableFuture.delayedExecutor(DELAY_MILLIS, TimeUnit.MILLISECONDS));
    }
}
