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

/**
 * Configuration for OpenTelemetry tracing.
 *
 * @author Nemanja Mikic
 * @since 1.0
 */
public class TracingDefaultConfiguration implements TracingConfiguration {

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    private boolean enabled = DEFAULT_ENABLED;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable/disable OpenTelemetry. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled true to enable OpenTelemetry
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
