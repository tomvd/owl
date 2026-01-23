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
 * Primary interface that all Owl services must implement.
 * <p>
 * Services are responsible for processing weather data or providing
 * additional functionality on top of the core platform. Examples include:
 * <ul>
 *   <li>Exporting data to external systems</li>
 *   <li>Generating reports or alerts</li>
 *   <li>Aggregating or transforming data</li>
 * </ul>
 * <p>
 * Thread Safety: Implementations must be thread-safe as methods may be
 * called from different threads.
 */
public interface OwlService {

    /**
     * Unique identifier for this service.
     * Should follow pattern: "service-type" (e.g., "export", "alert")
     *
     * @return unique service name
     */
    String getName();

    /**
     * Human-readable display name.
     *
     * @return display name (e.g., "Data Export Service")
     */
    String getDisplayName();

    /**
     * Version of this service implementation.
     * Follows semantic versioning: MAJOR.MINOR.PATCH
     *
     * @return version string
     */
    String getVersion();

    /**
     * Initialize and start the service.
     * <p>
     * This method should:
     * <ul>
     *   <li>Initialize any required resources</li>
     *   <li>Subscribe to relevant message bus topics</li>
     *   <li>Start background processing if needed</li>
     * </ul>
     * <p>
     * This method should return quickly. Long-running operations should
     * be performed in background threads.
     *
     * @param context provides access to message bus, configuration, and services
     * @throws ServiceException if service cannot start
     */
    void start(ServiceContext context) throws ServiceException;

    /**
     * Gracefully stop the service.
     * <p>
     * This method should:
     * <ul>
     *   <li>Stop processing</li>
     *   <li>Unsubscribe from message bus</li>
     *   <li>Clean up resources</li>
     *   <li>Stop background threads</li>
     * </ul>
     *
     * @throws ServiceException if service cannot stop cleanly
     */
    void stop() throws ServiceException;

    /**
     * Get current health status of the service.
     * Called periodically by the framework for monitoring.
     *
     * @return current health status
     */
    ServiceHealth getHealth();
}
