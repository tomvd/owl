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
 * Adapters are Micronaut beans annotated with {@code @Singleton} and
 * {@code @Requires(property = "owl.adapters.{name}.enabled", value = "true")}.
 * Lifecycle is managed via {@code @PostConstruct} and {@code @PreDestroy}.
 */
public interface WeatherAdapter {

    /**
     * Unique identifier for this adapter.
     * Should follow pattern: "source-type" (e.g., "davis-serial", "metar-http")
     */
    String getName();

    /**
     * Human-readable display name.
     */
    String getDisplayName();

    /**
     * Version of this adapter implementation.
     * Follows semantic versioning: MAJOR.MINOR.PATCH
     */
    String getVersion();

    /**
     * List of entities this adapter will provide.
     * Called during adapter registration to populate the entity registry.
     */
    List<EntityDefinition> getProvidedEntities();

    /**
     * Get current health status of the adapter.
     * Called periodically by the framework for monitoring.
     */
    AdapterHealth getHealth();

    /**
     * Check if the adapter supports data recovery/backfill.
     * If true, the framework may call requestRecovery() during startup.
     */
    default boolean supportsRecovery() {
        return false;
    }

    /**
     * Request the adapter to recover/backfill data for a time range.
     * Only called if supportsRecovery() returns true.
     */
    default RecoveryHandle requestRecovery(Instant fromTime, Instant toTime)
            throws AdapterException {
        throw new UnsupportedOperationException("Adapter does not support recovery");
    }
}
