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
package io.micronaut.tracing.brave.sender;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.tracing.brave.BraveTracerConfiguration.HttpClientSenderConfiguration;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import zipkin2.reporter.Sender;

/**
 * Factory that creates a Zipkin {@code Sender} based on {@code HttpClientSenderConfiguration}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(beans = HttpClientSenderConfiguration.class)
public class HttpClientSenderFactory {

    private final HttpClientSenderConfiguration configuration;

    /**
     * @param configuration the HTTP client sender configurations
     */
    protected HttpClientSenderFactory(HttpClientSenderConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * @param loadBalancerResolver a resolver capable of resolving references
     *                             to services into a concrete loadbalancer
     * @return the sender
     */
    @Singleton
    @Requires(missingBeans = Sender.class)
    Sender zipkinSender(Provider<LoadBalancerResolver> loadBalancerResolver) {
        return configuration.getBuilder()
                .build(loadBalancerResolver);
    }
}
