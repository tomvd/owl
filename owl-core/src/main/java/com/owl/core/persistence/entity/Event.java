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
package com.owl.core.persistence.entity;

import com.owl.core.persistence.converter.JsonAttributeConverter;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Map;

/**
 * Raw sensor event - stores individual measurements.
 * Maps to the "events" hypertable (TimescaleDB).
 */
@Serdeable
@MappedEntity("events")
public record Event(
        @EmbeddedId
        EventId id,

        Double state,

        @MappedProperty("attributes")
        @TypeDef(type = DataType.JSON, converter = JsonAttributeConverter.class)
        Map<String, Object> attributes
) {
    /**
     * Convenience constructor for creating events without attributes.
     */
    public static Event of(Instant timestamp, String entityId, Double state) {
        return new Event(new EventId(timestamp, entityId), state, null);
    }

    /**
     * Convenience constructor for creating events with attributes.
     */
    public static Event of(Instant timestamp, String entityId, Double state, Map<String, Object> attributes) {
        return new Event(new EventId(timestamp, entityId), state, attributes);
    }

    /**
     * Get the timestamp from the composite key.
     */
    public Instant timestamp() {
        return id.timestamp();
    }

    /**
     * Get the entity ID from the composite key.
     */
    public String entityId() {
        return id.entityId();
    }
}
