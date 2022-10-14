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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.tracing.brave.BraveTracerConfiguration.HttpClientSenderConfiguration;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import zipkin2.reporter.Sender;

import java.util.stream.Collectors;

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
    private final BeanProvider<InvocationInstrumenterFactory> invocationInstrumenterFactories;

    /**
     * @param configuration the HTTP client sender configurations
     * @param factories     the factories to instrument HTTP client Netty handlers execution with
     */
    protected HttpClientSenderFactory(HttpClientSenderConfiguration configuration,
                                      BeanProvider<InvocationInstrumenterFactory> factories) {
        this.configuration = configuration;
        this.invocationInstrumenterFactories = factories;
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
                .invocationInstrumenterFactories(invocationInstrumenterFactories.stream().collect(Collectors.toList()))
                .build(loadBalancerResolver);
    }
}
