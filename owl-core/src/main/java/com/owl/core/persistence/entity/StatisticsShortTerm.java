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
 * Short-term statistics - 5-minute aggregates.
 * Maps to the "statistics_short_term" hypertable (TimescaleDB).
 */
@Serdeable
@MappedEntity("statistics_short_term")
public record StatisticsShortTerm(
        @EmbeddedId
        StatisticsId id,

        Double mean,
        Double min,
        Double max,
        Double last,
        Double sum,
        Integer count,

        @MappedProperty("attributes")
        @TypeDef(type = DataType.JSON, converter = JsonAttributeConverter.class)
        Map<String, Object> attributes
) {
    /**
     * Create statistics from computed values.
     */
    public static StatisticsShortTerm of(
            Instant startTs,
            String entityId,
            Double mean,
            Double min,
            Double max,
            Double last,
            Double sum,
            Integer count) {
        return new StatisticsShortTerm(
                new StatisticsId(startTs, entityId),
                mean, min, max, last, sum, count, null
        );
    }

    /**
     * Create statistics from computed values with attributes.
     */
    public static StatisticsShortTerm of(
            Instant startTs,
            String entityId,
            Double mean,
            Double min,
            Double max,
            Double last,
            Double sum,
            Integer count,
            Map<String, Object> attributes) {
        return new StatisticsShortTerm(
                new StatisticsId(startTs, entityId),
                mean, min, max, last, sum, count, attributes
        );
    }

    /**
     * Create a gap-fill record with a single value for all fields.
     * Count is set to 0 to indicate this is a gap-fill record.
     */
    public static StatisticsShortTerm gapFill(Instant startTs, String entityId, Double value) {
        return new StatisticsShortTerm(
                new StatisticsId(startTs, entityId),
                value, value, value, value, value, 0, null
        );
    }

    /**
     * Create a gap-fill record with attributes (for non-numeric entities like icons).
     */
    public static StatisticsShortTerm gapFillWithAttributes(Instant startTs, String entityId, Map<String, Object> attributes) {
        return new StatisticsShortTerm(
                new StatisticsId(startTs, entityId),
                null, null, null, null, null, 0, attributes
        );
    }

    /**
     * Create a record for non-numeric entities (only attributes, no numeric stats).
     */
    public static StatisticsShortTerm ofAttributes(Instant startTs, String entityId, Map<String, Object> attributes, Integer count) {
        return new StatisticsShortTerm(
                new StatisticsId(startTs, entityId),
                null, null, null, null, null, count, attributes
        );
    }

    /**
     * Get the start timestamp from the composite key.
     */
    public Instant startTs() {
        return id.startTs();
    }

    /**
     * Get the entity ID from the composite key.
     */
    public String entityId() {
        return id.entityId();
    }
}
