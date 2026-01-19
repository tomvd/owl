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

import java.time.Instant;
import java.util.List;

/**
 * Primary interface that all weather data adapters must implement.
 * <p>
 * Adapters are responsible for:
 * <ul>
 *   <li>Connecting to and reading from their data source</li>
 *   <li>Parsing data into WeatherEvent objects</li>
 *   <li>Publishing events to the message bus</li>
 *   <li>Managing their own connection lifecycle</li>
 * </ul>
 * <p>
 * Thread Safety: Implementations must be thread-safe as methods may be
 * called from different threads.
 */
public interface WeatherAdapter {

    /**
     * Unique identifier for this adapter.
     * Should follow pattern: "source-type" (e.g., "davis-serial", "metar-http")
     *
     * @return unique adapter name
     */
    String getName();

    /**
     * Human-readable display name.
     *
     * @return display name (e.g., "Davis Vantage Pro Serial Adapter")
     */
    String getDisplayName();

    /**
     * Version of this adapter implementation.
     * Follows semantic versioning: MAJOR.MINOR.PATCH
     *
     * @return version string
     */
    String getVersion();

    /**
     * List of entities this adapter will provide.
     * Called during adapter registration to populate the entity registry.
     *
     * @return list of entity definitions
     */
    List<EntityDefinition> getProvidedEntities();

    /**
     * Initialize and start the adapter.
     * <p>
     * This method should:
     * <ul>
     *   <li>Establish connections to data sources</li>
     *   <li>Start reading/polling data</li>
     *   <li>Begin publishing events to the message bus</li>
     * </ul>
     * <p>
     * This method should return quickly. Long-running operations should
     * be performed in background threads.
     *
     * @param context provides access to message bus, configuration, and services
     * @throws AdapterException if adapter cannot start
     */
    void start(AdapterContext context) throws AdapterException;

    /**
     * Gracefully stop the adapter.
     * <p>
     * This method should:
     * <ul>
     *   <li>Stop reading data</li>
     *   <li>Close connections</li>
     *   <li>Clean up resources</li>
     *   <li>Stop background threads</li>
     * </ul>
     * <p>
     * After this method returns, no more events should be published.
     *
     * @throws AdapterException if adapter cannot stop cleanly
     */
    void stop() throws AdapterException;

    /**
     * Get current health status of the adapter.
     * Called periodically by the framework for monitoring.
     *
     * @return current health status
     */
    AdapterHealth getHealth();

    /**
     * Check if the adapter supports data recovery/backfill.
     * If true, the framework may call requestRecovery() during startup.
     *
     * @return true if adapter supports recovery
     */
    default boolean supportsRecovery() {
        return false;
    }

    /**
     * Request the adapter to recover/backfill data for a time range.
     * Only called if supportsRecovery() returns true.
     *
     * @param fromTime start of recovery period (inclusive)
     * @param toTime end of recovery period (exclusive)
     * @return recovery handle for tracking progress
     * @throws AdapterException if recovery cannot be initiated
     */
    default RecoveryHandle requestRecovery(Instant fromTime, Instant toTime)
            throws AdapterException {
        throw new UnsupportedOperationException("Adapter does not support recovery");
    }
}
