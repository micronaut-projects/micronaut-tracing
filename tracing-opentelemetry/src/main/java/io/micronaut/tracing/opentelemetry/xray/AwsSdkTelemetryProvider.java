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
package io.micronaut.tracing.opentelemetry.xray;

import io.micronaut.core.annotation.Internal;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

import java.util.function.Function;

/**
 * Provides AWS SDK telemetry operations without exposing the OpenTelemetry AWS SDK implementation as a bean.
 *
 * @author Nemanja Mikic
 * @since 8.0.0
 */
@Internal
final class AwsSdkTelemetryProvider {
    private final AwsSdkTelemetry awsSdkTelemetry;

    AwsSdkTelemetryProvider(AwsSdkTelemetry awsSdkTelemetry) {
        this.awsSdkTelemetry = awsSdkTelemetry;
    }

    ExecutionInterceptor newExecutionInterceptor() {
        return awsSdkTelemetry.createExecutionInterceptor();
    }

    <T> T withTelemetry(Function<AwsSdkTelemetry, T> function) {
        return function.apply(awsSdkTelemetry);
    }
}
