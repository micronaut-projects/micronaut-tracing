package io.micronaut.tracing.opentelemetry.sampler

import io.opentelemetry.sdk.common.Clock
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TokenBucketSpec extends Specification {

    static final long SECOND = TimeUnit.SECONDS.toNanos(1)

    FakeClock clock = new FakeClock()

    void "starts full with the burst capacity, then refills at the rate"() {
        given:
        def bucket = new TokenBucket(2, 4, clock)

        expect: 'the burst is available at once'
        (1..4).every { bucket.tryAcquire() }
        !bucket.tryAcquire()

        when: 'half a second refills one token'
        clock.advance(SECOND.intdiv(2))

        then:
        bucket.tryAcquire()
        !bucket.tryAcquire()

        when: 'a long idle period refills the bucket up to the burst only'
        clock.advance(60 * SECOND)

        then:
        (1..4).every { bucket.tryAcquire() }
        !bucket.tryAcquire()
    }

    void "a fractional rate allows one token per period"() {
        given:
        def bucket = new TokenBucket(0.5, 1, clock)

        expect:
        bucket.tryAcquire()
        !bucket.tryAcquire()

        when:
        clock.advance(SECOND)

        then:
        !bucket.tryAcquire()

        when:
        clock.advance(SECOND)

        then:
        bucket.tryAcquire()
    }

    void "a steady load is limited to the rate"() {
        given:
        def bucket = new TokenBucket(10, 10, clock)
        int acquired = 0

        when: '1000 attempts per second for 10 seconds'
        10_000.times {
            if (bucket.tryAcquire()) {
                acquired++
            }
            clock.advance(SECOND.intdiv(1000))
        }

        then: 'the initial burst, then a token every 100ms (at 0.1s, 0.2s ... 9.9s)'
        acquired == 10 + 99
    }

    void "concurrent callers never take more tokens than available"() {
        given:
        def bucket = new TokenBucket(1, 100, clock)
        def acquired = new AtomicInteger()
        def start = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(8)

        when:
        8.times {
            pool.submit {
                start.await()
                1000.times {
                    if (bucket.tryAcquire()) {
                        acquired.incrementAndGet()
                    }
                }
            }
        }
        start.countDown()
        pool.shutdown()

        then:
        pool.awaitTermination(30, TimeUnit.SECONDS)
        acquired.get() == 100
    }

    void "invalid arguments are rejected"() {
        when:
        new TokenBucket(rate, burst, clock)

        then:
        thrown(IllegalArgumentException)

        where:
        rate                     | burst
        0                        | 1
        -1                       | 1
        Double.NaN               | 1
        Double.POSITIVE_INFINITY | 1
        1                        | 0
    }

    static class FakeClock implements Clock {

        volatile long nanos = 1_000_000_000_000L

        void advance(long delta) {
            nanos += delta
        }

        @Override
        long now() {
            nanos
        }

        @Override
        long nanoTime() {
            nanos
        }
    }
}
