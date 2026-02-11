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

import com.owl.core.persistence.entity.StatisticsId;
import com.owl.core.persistence.entity.StatisticsShortTerm;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for short-term (5-minute) statistics.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface StatisticsShortTermRepository extends CrudRepository<StatisticsShortTerm, StatisticsId> {

    /**
     * Find all statistics within a time range (for hourly rollup).
     */
    @Query("SELECT * FROM statistics_short_term WHERE start_ts >= :start AND start_ts < :end ORDER BY start_ts")
    List<StatisticsShortTerm> findByTimeRange(Instant start, Instant end);

    /**
     * Find statistics for a specific entity within a time range.
     */
    @Query("SELECT * FROM statistics_short_term WHERE entity_id = :entityId AND start_ts >= :start AND start_ts < :end ORDER BY start_ts")
    List<StatisticsShortTerm> findByEntityIdAndTimeRange(String entityId, Instant start, Instant end);

    /**
     * Find the most recent statistics for an entity (for gap filling).
     */
    @Query("SELECT * FROM statistics_short_term WHERE entity_id = :entityId ORDER BY start_ts DESC LIMIT 1")
    Optional<StatisticsShortTerm> findLatestByEntityId(String entityId);

    /**
     * Check if statistics already exist for a given timestamp and entity (idempotency).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM statistics_short_term WHERE start_ts = :startTs AND entity_id = :entityId)")
    boolean existsByStartTsAndEntityId(Instant startTs, String entityId);
}
