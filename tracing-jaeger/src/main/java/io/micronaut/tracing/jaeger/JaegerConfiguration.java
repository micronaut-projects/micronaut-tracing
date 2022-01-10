/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tracing.jaeger;

import io.jaegertracing.Configuration;
import io.jaegertracing.Configuration.CodecConfiguration;
import io.jaegertracing.Configuration.ReporterConfiguration;
import io.jaegertracing.Configuration.SamplerConfiguration;
import io.jaegertracing.Configuration.SenderConfiguration;
import io.jaegertracing.spi.MetricsFactory;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;

import static io.jaegertracing.Configuration.JAEGER_SERVICE_NAME;
import static io.micronaut.context.env.Environment.DEFAULT_NAME;
import static io.micronaut.tracing.jaeger.JaegerConfiguration.PREFIX;

/**
 * Configuration for Jaeger tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(PREFIX)
public class JaegerConfiguration implements Toggleable {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.jaeger";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    @ConfigurationBuilder(prefixes = "with", includes = "tracerTags")
    protected final Configuration configuration;

    private boolean enabled = DEFAULT_ENABLED;
    private boolean expandExceptionLogs;
    private boolean zipkinSharedRpcSpan;

    /**
     * @param applicationConfiguration the common configurations
     */
    public JaegerConfiguration(ApplicationConfiguration applicationConfiguration) {
        if (StringUtils.isEmpty(CachedEnvironment.getProperty(JAEGER_SERVICE_NAME))
                && StringUtils.isEmpty(CachedEnvironment.getenv(JAEGER_SERVICE_NAME))) {
            System.setProperty(JAEGER_SERVICE_NAME, applicationConfiguration.getName().orElse(DEFAULT_NAME));
        }
        configuration = Configuration.fromEnv();
    }

    /**
     * @return whether to expand exception logs
     */
    public boolean isExpandExceptionLogs() {
        return expandExceptionLogs;
    }

    /**
     * Whether to expand exception logs.
     *
     * @param expandExceptionLogs true if they should be expanded
     */
    public void setExpandExceptionLogs(boolean expandExceptionLogs) {
        this.expandExceptionLogs = expandExceptionLogs;
    }

    /**
     * @return whether to use Zipkin shared RPC
     */
    public boolean isZipkinSharedRpcSpan() {
        return zipkinSharedRpcSpan;
    }

    /**
     * Whether to use Zipkin shared RPC.
     *
     * @param zipkinSharedRpcSpan true to use Zipkin shared RPC
     */
    public void setZipkinSharedRpcSpan(boolean zipkinSharedRpcSpan) {
        this.zipkinSharedRpcSpan = zipkinSharedRpcSpan;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable/disable Jaeger. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled true to enable Jaeger
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the Jaeger <code>Configuration</code>
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the sampler configuration.
     *
     * @param samplerConfiguration the configuration
     */
    @Inject
    public void setSamplerConfiguration(@Nullable SamplerConfiguration samplerConfiguration) {
        if (samplerConfiguration != null) {
            configuration.withSampler(samplerConfiguration);
        }
    }

    /**
     * Sets the reporter configuration.
     *
     * @param reporterConfiguration the configuration
     */
    @Inject
    public void setReporterConfiguration(@Nullable ReporterConfiguration reporterConfiguration) {
        if (reporterConfiguration != null) {
            configuration.withReporter(reporterConfiguration);
        }
    }

    /**
     * Sets the sampler configuration.
     *
     * @param samplerConfiguration the configuration
     */
    @Inject
    public void setSamplerConfiguration(@Nullable JaegerSamplerConfiguration samplerConfiguration) {
        if (samplerConfiguration != null) {
            configuration.withSampler(samplerConfiguration.configuration);
        }
    }

    /**
     * Sets the reporter configuration.
     *
     * @param reporterConfiguration the configuration
     */
    @Inject
    public void setReporterConfiguration(@Nullable JaegerReporterConfiguration reporterConfiguration) {
        if (reporterConfiguration != null) {
            configuration.withReporter(reporterConfiguration.configuration);
        }
    }

    /**
     * Sets the codec configuration.
     *
     * @param codecConfiguration the configuration
     */
    @Inject
    public void setCodecConfiguration(@Nullable CodecConfiguration codecConfiguration) {
        if (codecConfiguration != null) {
            configuration.withCodec(codecConfiguration);
        }
    }

    /**
     * Sets the metrics factory to use.
     *
     * @param metricsFactory the factory
     */
    @Inject
    void setMetricsFactory(@Nullable MetricsFactory metricsFactory) {
        if (metricsFactory != null) {
            configuration.withMetricsFactory(metricsFactory);
        }
    }

    /**
     * Set codecs from comma-delimited string.
     *
     * @param codecs the codecs
     */
    public void setCodecs(String codecs) {
        if (codecs != null) {
            setCodecConfiguration(CodecConfiguration.fromString(codecs));
        }
    }

    /**
     * The sampler configuration bean.
     */
    @ConfigurationProperties("sampler")
    public static class JaegerSamplerConfiguration {

        @ConfigurationBuilder(prefixes = "with")
        protected SamplerConfiguration configuration = SamplerConfiguration.fromEnv();

        /**
         * @return the <code>SamplerConfiguration</code>
         */
        public SamplerConfiguration getSamplerConfiguration() {
            return configuration;
        }

        /**
         * Sets the sampler probability used by the Jaeger sampler.
         * A value of 1.0 indicates to sample all requests.
         * A value of 0.1 indicates to sample 10% of requests.
         *
         * @param probability the sampler probability
         */
        public void setProbability(float probability) {
            configuration.withParam(probability);
        }
    }

    /**
     * The reporter configuration bean.
     */
    @ConfigurationProperties("reporter")
    public static class JaegerReporterConfiguration {

        @ConfigurationBuilder(prefixes = "with")
        protected ReporterConfiguration configuration = ReporterConfiguration.fromEnv();

        /**
         * @return the reporter configuration.
         */
        public ReporterConfiguration getReporterConfiguration() {
            return configuration;
        }

        /**
         * Sets the sender configuration.
         *
         * @param senderConfiguration the sender configuration
         */
        @Inject
        public void setSenderConfiguration(@Nullable SenderConfiguration senderConfiguration) {
            if (senderConfiguration != null) {
                configuration.withSender(senderConfiguration);
            }
        }

        /**
         * Sets the sender configuration.
         *
         * @param senderConfiguration the sender configuration
         */
        @Inject
        public void setSenderConfiguration(@Nullable JaegerSenderConfiguration senderConfiguration) {
            if (senderConfiguration != null) {
                configuration.withSender(senderConfiguration.configuration);
            }
        }
    }

    /**
     * The sender configuration bean.
     */
    @ConfigurationProperties("sender")
    public static class JaegerSenderConfiguration {

        @ConfigurationBuilder(prefixes = "with")
        protected SenderConfiguration configuration = SenderConfiguration.fromEnv();

        /**
         * @return the sender configuration
         */
        public SenderConfiguration getSenderConfiguration() {
            return configuration;
        }
    }
}
