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

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A sampler that refines the root decisions of another sampler (the one configured with
 * {@code otel.traces.sampler}):
 * <ol>
 *     <li>A span with a valid parent (local or remote) is sampled by the delegate unchanged, so the decision
 *     propagated by the caller is kept with a {@code parentbased_*} sampler.</li>
 *     <li>A root {@link SpanKind#SERVER server} span is sampled by the first matching
 *     {@link SamplingRuleConfiguration rule}, matched against its {@code url.path} and {@code http.route}
 *     attributes, or by the delegate when no rule matches.</li>
 *     <li>A root span that would be sampled is dropped when the {@link TokenBucket rate limit} of the new
 *     traces is exceeded.</li>
 * </ol>
 * The decision does not allocate, except the matchers of the regular expressions of the rules.
 *
 * @since 8.4.0
 */
@Internal
final class RootSampler implements Sampler {

    static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
    static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");

    private final Sampler delegate;
    private final Rule[] rules;
    @Nullable
    private final TokenBucket rateLimit;
    private final String description;

    /**
     * @param delegate      The sampler of the spans with a parent, and of the roots no rule matches
     * @param rules         The sampling rules
     * @param rateLimitConf The rate limit configuration
     * @param clock         The clock of the rate limit
     */
    RootSampler(Sampler delegate,
                List<SamplingRuleConfiguration> rules,
                SamplerConfiguration.RateLimitConfiguration rateLimitConf,
                Clock clock) {
        this.delegate = delegate;
        this.rules = rules.stream()
            .sorted(Comparator.comparingInt(SamplingRuleConfiguration::getIndex))
            .map(Rule::new)
            .toArray(Rule[]::new);
        Double tracesPerSecond = rateLimitConf.getTracesPerSecond();
        if (tracesPerSecond != null) {
            try {
                this.rateLimit = new TokenBucket(tracesPerSecond, rateLimitConf.effectiveBurst(), clock);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invalid " + SamplerConfiguration.PREFIX + ".rate-limit: " + e.getMessage(), e);
            }
        } else {
            this.rateLimit = null;
        }
        this.description = "MicronautRootSampler{rules=" + Arrays.toString(this.rules)
            + ", tracesPerSecond=" + tracesPerSecond
            + ", burst=" + (tracesPerSecond == null ? null : rateLimitConf.effectiveBurst())
            + ", delegate=" + delegate.getDescription() + '}';
    }

    @Override
    public SamplingResult shouldSample(Context parentContext,
                                       String traceId,
                                       String name,
                                       SpanKind spanKind,
                                       Attributes attributes,
                                       List<LinkData> parentLinks) {
        if (Span.fromContext(parentContext).getSpanContext().isValid()) {
            return delegate.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
        }
        Sampler sampler = delegate;
        if (spanKind == SpanKind.SERVER && rules.length > 0) {
            String path = attributes.get(URL_PATH);
            String route = attributes.get(HTTP_ROUTE);
            for (Rule rule : rules) {
                if (rule.matches(path, route)) {
                    sampler = rule.sampler;
                    break;
                }
            }
        }
        SamplingResult result = sampler.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
        if (rateLimit != null && result.getDecision() == SamplingDecision.RECORD_AND_SAMPLE && !rateLimit.tryAcquire()) {
            return SamplingResult.drop();
        }
        return result;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }

    /**
     * A compiled sampling rule.
     */
    private static final class Rule {

        @Nullable
        private final Pattern path;
        @Nullable
        private final Pattern route;
        private final Sampler sampler;
        private final String description;

        Rule(SamplingRuleConfiguration configuration) {
            String prefix = SamplingRuleConfiguration.PREFIX + "[" + configuration.getIndex() + "]";
            if (configuration.getPath() == null && configuration.getRoute() == null) {
                throw new ConfigurationException(prefix + " must set a path or a route pattern");
            }
            try {
                path = configuration.getPath() == null ? null : Pattern.compile(configuration.getPath());
                route = configuration.getRoute() == null ? null : Pattern.compile(configuration.getRoute());
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException("Invalid pattern of " + prefix + ": " + e.getMessage(), e);
            }
            double ratio = configuration.getRatio();
            if (!(ratio >= 0 && ratio <= 1)) {
                throw new ConfigurationException(prefix + ".ratio must be between 0 and 1: " + ratio);
            }
            if (ratio == 0) {
                sampler = Sampler.alwaysOff();
            } else if (ratio == 1) {
                sampler = Sampler.alwaysOn();
            } else {
                sampler = Sampler.traceIdRatioBased(ratio);
            }
            description = "{path=" + path + ", route=" + route + ", sampler=" + sampler.getDescription() + '}';
        }

        boolean matches(@Nullable String requestPath, @Nullable String requestRoute) {
            if (path != null && (requestPath == null || !path.matcher(requestPath).matches())) {
                return false;
            }
            return route == null || requestRoute != null && route.matcher(requestRoute).matches();
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
