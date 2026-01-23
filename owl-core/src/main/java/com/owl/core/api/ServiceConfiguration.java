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

import java.util.Map;
import java.util.Optional;

/**
 * Configuration interface for services.
 * <p>
 * Provides access to service-specific configuration values from application.yml.
 */
public interface ServiceConfiguration {

    /**
     * Check if service is enabled in configuration.
     *
     * @return true if enabled
     */
    boolean isEnabled();

    /**
     * Get the service name this configuration belongs to.
     *
     * @return service name
     */
    String getServiceName();

    /**
     * Get a string configuration value.
     *
     * @param key configuration key
     * @return value or empty if not present
     */
    Optional<String> getString(String key);

    /**
     * Get an integer configuration value.
     *
     * @param key configuration key
     * @return value or empty if not present
     */
    Optional<Integer> getInt(String key);

    /**
     * Get a long configuration value.
     *
     * @param key configuration key
     * @return value or empty if not present
     */
    Optional<Long> getLong(String key);

    /**
     * Get a boolean configuration value.
     *
     * @param key configuration key
     * @return value or empty if not present
     */
    Optional<Boolean> getBoolean(String key);

    /**
     * Get a double configuration value.
     *
     * @param key configuration key
     * @return value or empty if not present
     */
    Optional<Double> getDouble(String key);

    /**
     * Get all configuration as a map.
     *
     * @return configuration map
     */
    Map<String, Object> asMap();

    /**
     * Get a required string value, throws if not present.
     *
     * @param key configuration key
     * @return value
     * @throws ConfigurationException if not present
     */
    default String getRequiredString(String key) {
        return getString(key).orElseThrow(() ->
                new ConfigurationException("Required configuration missing: " + key));
    }

    /**
     * Get a required integer value, throws if not present.
     *
     * @param key configuration key
     * @return value
     * @throws ConfigurationException if not present
     */
    default int getRequiredInt(String key) {
        return getInt(key).orElseThrow(() ->
                new ConfigurationException("Required configuration missing: " + key));
    }

    /**
     * Get a required double value, throws if not present.
     *
     * @param key configuration key
     * @return value
     * @throws ConfigurationException if not present
     */
    default double getRequiredDouble(String key) {
        return getDouble(key).orElseThrow(() ->
                new ConfigurationException("Required configuration missing: " + key));
    }
}
