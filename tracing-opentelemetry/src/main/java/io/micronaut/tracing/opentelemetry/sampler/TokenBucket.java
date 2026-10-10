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
package io.micronaut.tracing.opentelemetry.sampler;

import io.micronaut.core.annotation.Internal;
import io.opentelemetry.sdk.common.Clock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe, allocation-free token bucket.
 * <p>
 * The bucket holds up to {@code burst} tokens and is refilled continuously at {@code tokensPerSecond}. It
 * starts full. Instead of a token count, it keeps a single timestamp: the time at which the bucket would be
 * empty (a generic cell rate algorithm). Taking a token moves that time forward by the refill interval of
 * one token, which is allowed as long as it does not pass the current time. The state is updated with a
 * compare-and-set, so concurrent callers never take more tokens than available.
 *
 * @since 8.4.0
 */
@Internal
final class TokenBucket {

    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final Clock clock;
    private final long nanosPerToken;
    private final long capacityNanos;
    private final AtomicLong emptyAt;

    /**
     * @param tokensPerSecond The refill rate, greater than zero
     * @param burst           The capacity of the bucket in tokens, at least 1
     * @param clock           The clock
     */
    TokenBucket(double tokensPerSecond, int burst, Clock clock) {
        if (!(tokensPerSecond > 0) || Double.isInfinite(tokensPerSecond)) {
            throw new IllegalArgumentException("The rate must be a positive number: " + tokensPerSecond);
        }
        if (burst < 1) {
            throw new IllegalArgumentException("The burst must be at least 1: " + burst);
        }
        this.clock = clock;
        this.nanosPerToken = Math.max(1L, (long) (NANOS_PER_SECOND / tokensPerSecond));
        this.capacityNanos = nanosPerToken * burst;
        this.emptyAt = new AtomicLong(clock.nanoTime() - capacityNanos);
    }

    /**
     * Takes a token if one is available.
     *
     * @return whether a token was taken
     */
    boolean tryAcquire() {
        long now = clock.nanoTime();
        while (true) {
            long current = emptyAt.get();
            // a bucket idle for longer than its capacity is full, not fuller
            long next = Math.max(current, now - capacityNanos) + nanosPerToken;
            if (next - now > 0) {
                return false;
            }
            if (emptyAt.compareAndSet(current, next)) {
                return true;
            }
        }
    }
}
