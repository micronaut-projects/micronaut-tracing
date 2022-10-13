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

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.tracing.brave.ZipkinServiceInstanceList;
import jakarta.inject.Provider;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.http.HttpRequest.POST;
import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.MULTIPLE_CHOICES;
import static io.micronaut.tracing.brave.sender.HttpClientSender.Builder.DEFAULT_PATH;
import static reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER;
import static zipkin2.CheckResult.OK;
import static zipkin2.codec.Encoding.JSON;

/**
 * A {@code Sender} implementation that uses Micronaut's {@code HttpClient}.
 *
 * @author graemerocher
 * @since 1.0
 */
public final class HttpClientSender extends Sender {

    private final Encoding encoding;
    private final int messageMaxBytes;
    private final boolean compressionEnabled;
    private final URI endpoint;
    private final List<InvocationInstrumenterFactory> factories;
    private final Provider<LoadBalancerResolver> loadBalancerResolver;
    private final HttpClientConfiguration clientConfiguration;
    private HttpClient httpClient;

    private HttpClientSender(Encoding encoding,
                             int messageMaxBytes,
                             boolean compressionEnabled,
                             HttpClientConfiguration clientConfiguration,
                             Provider<LoadBalancerResolver> loadBalancerResolver,
                             String path,
                             List<InvocationInstrumenterFactory> factories) {
        this.loadBalancerResolver = loadBalancerResolver;
        this.clientConfiguration = clientConfiguration;
        this.encoding = encoding;
        this.messageMaxBytes = messageMaxBytes;
        this.compressionEnabled = compressionEnabled;
        this.factories = factories;
        endpoint = path == null ? URI.create(DEFAULT_PATH) : URI.create(path);
    }

    @Override
    public Encoding encoding() {
        return encoding;
    }

    @Override
    public int messageMaxBytes() {
        return messageMaxBytes;
    }

    @Override
    public int messageSizeInBytes(List<byte[]> encodedSpans) {
        return encoding().listSizeInBytes(encodedSpans);
    }

    @Override
    public Call<Void> sendSpans(List<byte[]> encodedSpans) {
        initHttpClient();
        if (httpClient == null || !httpClient.isRunning()) {
            throw new IllegalStateException("HTTP Client Closed");
        }

        return new HttpCall(httpClient, endpoint, compressionEnabled, encodedSpans);
    }

    @Override
    public CheckResult check() {
        initHttpClient();

        if (httpClient == null) {
            return CheckResult.failed(new NoAvailableServiceException(ZipkinServiceInstanceList.SERVICE_ID));
        }

        try {
            HttpResponse<Object> response = httpClient
                    .toBlocking()
                    .exchange(POST(endpoint, Collections.emptyList()));
            if (response.getStatus().getCode() >= MULTIPLE_CHOICES.getCode()) {
                throw new IllegalStateException("check response failed: " + response);
            }
            return OK;
        } catch (Exception e) {
            return CheckResult.failed(e);
        }
    }

    private void initHttpClient() {
        if (httpClient != null) {
            return;
        }

        Optional<? extends LoadBalancer> loadBalancer = loadBalancerResolver.get()
                .resolve(ZipkinServiceInstanceList.SERVICE_ID);

        httpClient = loadBalancer.map(lb -> new DefaultHttpClient(lb, clientConfiguration, factories))
                .orElse(null);
    }

    @Override
    public void close() {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * The HTTP call.
     */
    private static class HttpCall extends Call<Void> {

        private final HttpClient httpClient;
        private final URI endpoint;
        private final boolean compressionEnabled;
        private final List<byte[]> encodedSpans;

        private final AtomicReference<Subscription> subscription = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        HttpCall(HttpClient httpClient,
                 URI endpoint,
                 boolean compressionEnabled,
                 List<byte[]> encodedSpans) {
            this.httpClient = httpClient;
            this.endpoint = endpoint;
            this.compressionEnabled = compressionEnabled;
            this.encodedSpans = encodedSpans;
        }

        @Override
        public Void execute() {
            HttpResponse<Object> response = httpClient.toBlocking().exchange(prepareRequest());
            if (response.getStatus().getCode() >= BAD_REQUEST.getCode()) {
                throw new IllegalStateException("Response return invalid status code: " + response.getStatus());
            }
            return null;
        }

        @Override
        public void enqueue(Callback<Void> callback) {
            Publisher<HttpResponse<ByteBuffer>> publisher = httpClient.exchange(prepareRequest());
            publisher.subscribe(new Subscriber<HttpResponse<ByteBuffer>>() {

                @Override
                public void onSubscribe(Subscription s) {
                    subscription.set(s);
                    s.request(1);
                }

                @Override
                public void onNext(HttpResponse<ByteBuffer> response) {
                    if (response.getStatus().getCode() >= BAD_REQUEST.getCode()) {
                        callback.onError(new IllegalStateException("Response return invalid status code: " + response.getStatus()));
                    } else {
                        callback.onSuccess(null);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    callback.onError(t);
                }

                @Override
                public void onComplete() {
                }
            });
        }

        @Override
        public void cancel() {
            Subscription s = subscription.get();
            if (s != null) {
                cancelled.set(true);
                s.cancel();
            }
        }

        @Override
        public boolean isCanceled() {
            Subscription s = subscription.get();
            return s != null && cancelled.get();
        }

        @Override
        public Call<Void> clone() {
            // stateless. no need to clone
            return new HttpCall(httpClient, endpoint, compressionEnabled, encodedSpans);
        }

        protected MutableHttpRequest<Flux<Object>> prepareRequest() {
            return POST(endpoint, spanReactiveSequence());
        }

        private Flux<Object> spanReactiveSequence() {
            return Flux.create(emitter -> {
                for (byte[] encodedSpan : encodedSpans) {
                    emitter.next(encodedSpan);
                }
                emitter.complete();
            }, BUFFER);
        }
    }

    /**
     * Constructs the {@code HttpClientSender}.
     */
    public static class Builder {

        public static final String DEFAULT_PATH = "/api/v2/spans";
        public static final String DEFAULT_SERVER_URL = "http://localhost:9411";

        private Encoding encoding = JSON;
        private int messageMaxBytes = 5 * 1024;
        private String path = DEFAULT_PATH;
        private boolean compressionEnabled = true;
        private List<URI> servers = Collections.singletonList(URI.create(DEFAULT_SERVER_URL));
        private final HttpClientConfiguration clientConfiguration;
        private List<InvocationInstrumenterFactory> invocationInstrumenterFactories;

        /**
         * @param clientConfiguration the HTTP client configuration
         */
        public Builder(HttpClientConfiguration clientConfiguration) {
            this.clientConfiguration = clientConfiguration;
        }

        /**
         * @return the configured Zipkin servers
         */
        public List<URI> getServers() {
            return servers;
        }

        /**
         * The encoding to use. Defaults to {@link Encoding#JSON}
         *
         * @param encoding the encoding
         * @return this
         */
        public Builder encoding(Encoding encoding) {
            if (encoding != null) {
                this.encoding = encoding;
            }
            return this;
        }

        /**
         * The message max bytes.
         *
         * @param messageMaxBytes the max bytes
         * @return this
         */
        public Builder messageMaxBytes(int messageMaxBytes) {
            this.messageMaxBytes = messageMaxBytes;
            return this;
        }

        /**
         * Whether compression is enabled (defaults to true).
         *
         * @param compressionEnabled true if compression is enabled
         * @return this
         */
        public Builder compressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        /**
         * The endpoint to use.
         *
         * @param endpoint the fully qualified URI of the Zipkin endpoint
         * @return this
         */
        public Builder server(URI endpoint) {
            if (endpoint != null) {
                servers = Collections.singletonList(endpoint);
            }
            return this;
        }

        /**
         * The endpoint to use.
         *
         * @param endpoint the fully qualified URI of the Zipkin endpoint
         * @return this
         */
        public Builder url(URI endpoint) {
            return server(endpoint);
        }

        /**
         * The endpoint to use.
         *
         * @param urls the Zipkin server URLs
         * @return this
         */
        public Builder urls(List<URI> urls) {
            if (CollectionUtils.isNotEmpty(urls)) {
                servers = Collections.unmodifiableList(urls);
            }
            return this;
        }

        /**
         * The path to use.
         *
         * @param path the path of the Zipkin endpoint
         * @return this
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * The invocation instrumenter factories to use.
         *
         * @param factories the factories to instrument HTTP client Netty handlers execution with
         * @return this
         */
        public Builder invocationInstrumenterFactories(List<InvocationInstrumenterFactory> factories) {
            this.invocationInstrumenterFactories = factories;
            return this;
        }

        /**
         * Constructs a {@code HttpClientSender}.
         *
         * @param loadBalancerResolver resolver capable of resolving references
         *                             to services into a concrete load-balancer
         * @return the sender
         */
        public HttpClientSender build(Provider<LoadBalancerResolver> loadBalancerResolver) {
            return new HttpClientSender(
                    encoding,
                    messageMaxBytes,
                    compressionEnabled,
                    clientConfiguration,
                    loadBalancerResolver,
                    path,
                    invocationInstrumenterFactories
            );
        }
    }
}
