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
package io.micronaut.tracing.opentelemetry.instrument.http.client;

import java.net.InetSocketAddress;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;

import static io.micronaut.http.HttpAttributes.SERVICE_ID;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;

@Internal
enum MicronautHttpNetClientAttributesGetter implements NetClientAttributesGetter<MutableHttpRequest<Object>, HttpResponse<Object>> {

    INSTANCE;

    public InetSocketAddress getAddress(MutableHttpRequest<Object> request) {
        return request.getRemoteAddress();
    }

    @Override
    public String getTransport(MutableHttpRequest<Object> request,
                               @Nullable HttpResponse<Object> response) {
        return IP_TCP;
    }

    @Override
    public String getServerAddress(MutableHttpRequest<Object> request) {
        return request.getAttribute(SERVICE_ID, String.class)
            .filter(serviceId -> !serviceId.contains("/"))
            .orElseGet(() -> getAddress(request).getHostString());
    }

    @Override
    public Integer getServerPort(MutableHttpRequest<Object> request) {
        return getAddress(request).getPort();
    }
}
