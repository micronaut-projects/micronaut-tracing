package io.micronaut.tracing.opentelemetry.sampler

import io.micronaut.context.exceptions.ConfigurationException
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import static io.opentelemetry.sdk.trace.samplers.SamplingDecision.DROP
import static io.opentelemetry.sdk.trace.samplers.SamplingDecision.RECORD_AND_SAMPLE

class RootSamplerSpec extends Specification {

    static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c"

    TokenBucketSpec.FakeClock clock = new TokenBucketSpec.FakeClock()

    void "root decisions are limited to the rate, children follow their parent"() {
        given:
        def sampler = sampler(Sampler.parentBased(Sampler.alwaysOn()), [], 1, 2)

        expect: 'the burst of new traces'
        decide(sampler, Context.root()) == RECORD_AND_SAMPLE
        decide(sampler, Context.root()) == RECORD_AND_SAMPLE
        decide(sampler, Context.root()) == DROP

        and: 'spans with a sampled parent are not limited, unsampled parents are kept'
        (1..10).every { decide(sampler, parent(true)) == RECORD_AND_SAMPLE }
        decide(sampler, parent(false)) == DROP
        decide(sampler, remoteParent(true)) == RECORD_AND_SAMPLE

        when:
        clock.advance(TimeUnit.SECONDS.toNanos(1))

        then:
        decide(sampler, Context.root()) == RECORD_AND_SAMPLE
        decide(sampler, Context.root()) == DROP
    }

    void "the roots dropped by the delegate do not use tokens"() {
        given:
        def sampler = sampler(Sampler.alwaysOff(), [rule(0, "/always", null, 1)], 1, 1)

        expect:
        decide(sampler, Context.root(), "/other") == DROP
        decide(sampler, Context.root(), "/other") == DROP
        decide(sampler, Context.root(), "/always") == RECORD_AND_SAMPLE
        decide(sampler, Context.root(), "/always") == DROP
    }

    void "the first matching rule decides the sampling of a root server span"() {
        given:
        def sampler = sampler(Sampler.alwaysOn(), [
            rule(1, "/internal/.*", null, 1),
            rule(0, "/internal/health", null, 0),
            rule(2, null, "/books/\\{id\\}", 0)
        ], null, null)

        expect:
        decide(sampler, Context.root(), path, route, kind) == decision

        where:
        path               | route         | kind            | decision
        "/internal/health" | null          | SpanKind.SERVER | DROP
        "/internal/other"  | null          | SpanKind.SERVER | RECORD_AND_SAMPLE
        "/books/1"         | "/books/{id}" | SpanKind.SERVER | DROP
        "/books"           | "/books"      | SpanKind.SERVER | RECORD_AND_SAMPLE
        "/other"           | null          | SpanKind.SERVER | RECORD_AND_SAMPLE
        "/internal/health" | null          | SpanKind.CLIENT | RECORD_AND_SAMPLE
        null               | null          | SpanKind.SERVER | RECORD_AND_SAMPLE
    }

    void "a rule with a path and a route needs both to match"() {
        given:
        def sampler = sampler(Sampler.alwaysOn(), [rule(0, "/books/1", "/books/\\{id\\}", 0)], null, null)

        expect:
        decide(sampler, Context.root(), "/books/1", "/books/{id}") == DROP
        decide(sampler, Context.root(), "/books/2", "/books/{id}") == RECORD_AND_SAMPLE
        decide(sampler, Context.root(), "/books/1", null) == RECORD_AND_SAMPLE
    }

    void "rules do not apply to spans with a parent"() {
        given:
        def sampler = sampler(Sampler.parentBased(Sampler.alwaysOn()), [rule(0, "/.*", null, 0)], null, null)

        expect:
        decide(sampler, remoteParent(true), "/health") == RECORD_AND_SAMPLE
        decide(sampler, Context.root(), "/health") == DROP
    }

    void "a ratio rule uses the trace id"() {
        given:
        def sampler = sampler(Sampler.alwaysOn(), [rule(0, "/.*", null, 0.5)], null, null)

        expect:
        decide(sampler, Context.root(), "/a", null, SpanKind.SERVER, "00000000000000000000000000000001") == RECORD_AND_SAMPLE
        decide(sampler, Context.root(), "/a", null, SpanKind.SERVER, "00000000000000007fffffffffffffff") == DROP
    }

    void "invalid rules fail with a configuration exception"() {
        when:
        sampler(Sampler.alwaysOn(), [rule(0, path, null, ratio)], null, null)

        then:
        thrown(ConfigurationException)

        where:
        path    | ratio
        null    | 0
        "/(a"   | 0
        "/a"    | 1.5
        "/a"    | -1
    }

    void "an invalid rate limit fails with a configuration exception"() {
        when:
        sampler(Sampler.alwaysOn(), [], 0, null)

        then:
        thrown(ConfigurationException)
    }

    void "the description lists the rules, the rate limit and the delegate"() {
        expect:
        sampler(Sampler.parentBased(Sampler.alwaysOn()), [rule(0, "/a", null, 0)], 5, null).description ==
            "MicronautRootSampler{rules=[{path=/a, route=null, sampler=AlwaysOffSampler}], tracesPerSecond=5.0, burst=5, delegate=ParentBased{root:AlwaysOnSampler,remoteParentSampled:AlwaysOnSampler,remoteParentNotSampled:AlwaysOffSampler,localParentSampled:AlwaysOnSampler,localParentNotSampled:AlwaysOffSampler}}"
    }

    private RootSampler sampler(Sampler delegate, List<SamplingRuleConfiguration> rules, Number tracesPerSecond, Integer burst) {
        def rateLimit = new SamplerConfiguration.RateLimitConfiguration()
        rateLimit.tracesPerSecond = tracesPerSecond?.doubleValue()
        rateLimit.burst = burst
        new RootSampler(delegate, rules, rateLimit, clock)
    }

    private static SamplingRuleConfiguration rule(int index, String path, String route, Number ratio) {
        def rule = new SamplingRuleConfiguration(index)
        rule.path = path
        rule.route = route
        rule.ratio = ratio.doubleValue()
        rule
    }

    private static SamplingDecision decide(Sampler sampler, Context parent, String path = "/", String route = null,
                                           SpanKind kind = SpanKind.SERVER, String traceId = TRACE_ID) {
        def attributes = Attributes.builder()
        if (path != null) {
            attributes.put(RootSampler.URL_PATH, path)
        }
        if (route != null) {
            attributes.put(RootSampler.HTTP_ROUTE, route)
        }
        sampler.shouldSample(parent, traceId, "span", kind, attributes.build(), []).decision
    }

    private static Context parent(boolean sampled) {
        Context.root().with(Span.wrap(SpanContext.create(TRACE_ID, "b7ad6b7169203331",
            sampled ? TraceFlags.sampled : TraceFlags.default, TraceState.default)))
    }

    private static Context remoteParent(boolean sampled) {
        Context.root().with(Span.wrap(SpanContext.createFromRemoteParent(TRACE_ID, "b7ad6b7169203331",
            sampled ? TraceFlags.sampled : TraceFlags.default, TraceState.default)))
    }
}
