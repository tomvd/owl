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
package com.owl.core.persistence.repository;

import com.owl.core.persistence.entity.Event;
import com.owl.core.persistence.entity.EventId;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for raw sensor events.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface EventRepository extends CrudRepository<Event, EventId> {

    /**
     * Find events for a specific entity within a time range.
     */
    @Query("SELECT * FROM events WHERE entity_id = :entityId AND timestamp >= :start AND timestamp < :end ORDER BY timestamp")
    List<Event> findByEntityIdAndTimeRange(String entityId, Instant start, Instant end);

    /**
     * Find the most recent event for an entity.
     */
    @Query("SELECT * FROM events WHERE entity_id = :entityId ORDER BY timestamp DESC LIMIT 1")
    Optional<Event> findLatestByEntityId(String entityId);

    /**
     * Find the most recent timestamp for any entity from a specific source.
     * Used by recovery service to detect data gaps.
     */
    @Query("""
            SELECT MAX(e.timestamp) FROM events e
            JOIN entities ent ON e.entity_id = ent.entity_id
            WHERE ent.source = :source
            """)
    Optional<Instant> findLatestTimestampBySource(String source);

    /**
     * Find events for multiple entities within a time range.
     */
    @Query("SELECT * FROM events WHERE entity_id IN (:entityIds) AND timestamp >= :start AND timestamp < :end ORDER BY timestamp")
    List<Event> findByEntityIdsAndTimeRange(List<String> entityIds, Instant start, Instant end);

    /**
     * Count events in a time window for an entity.
     */
    @Query("SELECT COUNT(*) FROM events WHERE entity_id = :entityId AND timestamp >= :start AND timestamp < :end")
    long countByEntityIdAndTimeRange(String entityId, Instant start, Instant end);

    /**
     * Delete events older than a given timestamp for an entity.
     * Note: TimescaleDB retention policy handles this automatically, but useful for manual cleanup.
     */
    @Query("DELETE FROM events WHERE entity_id = :entityId AND timestamp < :before")
    void deleteByEntityIdAndTimestampBefore(String entityId, Instant before);
}
