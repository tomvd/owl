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

import com.owl.core.persistence.entity.Statistics;
import com.owl.core.persistence.entity.StatisticsId;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for long-term (hourly) statistics.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface StatisticsRepository extends CrudRepository<Statistics, StatisticsId> {

    /**
     * Find the most recent statistics for an entity.
     */
    @Query("SELECT * FROM statistics WHERE entity_id = :entityId ORDER BY start_ts DESC LIMIT 1")
    Optional<Statistics> findLatestByEntityId(String entityId);

    /**
     * Check if statistics already exist for a given timestamp and entity (idempotency).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM statistics WHERE start_ts = :startTs AND entity_id = :entityId)")
    boolean existsByStartTsAndEntityId(Instant startTs, String entityId);
}
