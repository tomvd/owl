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
import java.util.Map;
import java.util.Objects;

/**
 * Standard event for simple sensor readings.
 * <p>
 * Most adapters will emit these. Each reading represents a single
 * measurement of a specific entity at a point in time.
 */
public final class SensorReading implements WeatherEvent {

    private final Instant timestamp;
    private final String source;
    private final String entityId;
    private final double value;
    private final Map<String, Object> attributes;

    /**
     * Create a sensor reading with attributes.
     *
     * @param timestamp  when the measurement was taken
     * @param source     source adapter name
     * @param entityId   entity ID (e.g., "sensor.davis_temp_out")
     * @param value      numeric value
     * @param attributes optional metadata (may be null)
     */
    public SensorReading(
            Instant timestamp,
            String source,
            String entityId,
            double value,
            Map<String, Object> attributes) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.source = Objects.requireNonNull(source, "source");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.value = value;
        this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    /**
     * Create a sensor reading without attributes.
     *
     * @param timestamp when the measurement was taken
     * @param source    source adapter name
     * @param entityId  entity ID (e.g., "sensor.davis_temp_out")
     * @param value     numeric value
     */
    public SensorReading(Instant timestamp, String source, String entityId, double value) {
        this(timestamp, source, entityId, value, null);
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String getEventType() {
        return "sensor_reading";
    }

    public String getEntityId() {
        return entityId;
    }

    public double getValue() {
        return value;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SensorReading that = (SensorReading) o;
        return Double.compare(that.value, value) == 0 &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(source, that.source) &&
                Objects.equals(entityId, that.entityId) &&
                Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, source, entityId, value, attributes);
    }

    @Override
    public String toString() {
        return "SensorReading{" +
                "timestamp=" + timestamp +
                ", source='" + source + '\'' +
                ", entityId='" + entityId + '\'' +
                ", value=" + value +
                ", attributes=" + attributes +
                '}';
    }
}
