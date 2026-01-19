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

/**
 * Base interface for all weather events.
 * <p>
 * Weather events represent data collected from various sources.
 * They are published to the message bus and processed by the core system.
 */
public interface WeatherEvent {

    /**
     * Event timestamp (when the measurement was taken).
     *
     * @return event timestamp
     */
    Instant getTimestamp();

    /**
     * Source adapter name that produced this event.
     * Should match WeatherAdapter.getName()
     *
     * @return source adapter name
     */
    String getSource();

    /**
     * Get event type identifier.
     * Used for routing and processing.
     *
     * @return event type (e.g., "sensor_reading", "archive_record")
     */
    String getEventType();
}
