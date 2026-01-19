/*
 * Copyright 2025 Owl (OpenWeatherLink) Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.owl.core.api;

/**
 * Service Provider Interface for adapter discovery.
 * <p>
 * Each adapter module must provide an implementation of this interface
 * and register it using Java's ServiceLoader mechanism.
 * <p>
 * To register your adapter provider:
 * <ol>
 *   <li>Create a file: META-INF/services/com.owl.core.api.AdapterProvider</li>
 *   <li>Add the fully qualified class name of your provider implementation</li>
 * </ol>
 * <p>
 * Example file contents:
 * <pre>
 * com.owl.adapter.metar.MetarAdapterProvider
 * </pre>
 * <p>
 * The framework will automatically discover and load all registered providers
 * at startup.
 */
@FunctionalInterface
public interface AdapterProvider {

    /**
     * Create an instance of the weather adapter.
     * <p>
     * This method is called once during application startup for each
     * discovered provider. The framework will then call start() on the
     * created adapter if it is enabled in configuration.
     *
     * @return new adapter instance
     */
    WeatherAdapter createAdapter();

    /**
     * Get metadata about this adapter provider.
     * Used for displaying available adapters and debugging.
     *
     * @return provider metadata
     */
    default ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "unknown",
                "Unknown Adapter",
                "1.0.0",
                "Unknown",
                "No description provided"
        );
    }
}
