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
package io.micronaut.tracing;

import io.micronaut.context.annotation.ConfigurationProperties;


import static io.micronaut.tracing.AwsOpenTelemetryConfiguration.PREFIX;

/**
 * Configuration for aws tracing.
 *
 * @author Nemanja Mikic
 */
@ConfigurationProperties(PREFIX)
public class AwsOpenTelemetryConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "tracing.aws";

    private String otlpGrpcEndpoint;

    /**
     * @return key otlpGrpc endpoint url.
     */
    public String getOtlpGrpcEndpoint() {
        return otlpGrpcEndpoint;
    }

    /**
     * @param otlpGrpcEndpoint otlp Grpc url.
     */
    public void setOtlpGrpcEndpoint(String otlpGrpcEndpoint) {
        this.otlpGrpcEndpoint = otlpGrpcEndpoint;
    }

}
