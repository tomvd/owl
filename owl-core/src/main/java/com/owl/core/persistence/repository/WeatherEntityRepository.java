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

import com.owl.core.persistence.entity.WeatherEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

/**
 * Repository for weather entity metadata.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface WeatherEntityRepository extends CrudRepository<WeatherEntity, String> {

    /**
     * Find all entities from a specific source.
     */
    List<WeatherEntity> findBySource(String source);

    /**
     * Find all entities with a specific aggregation method.
     */
    List<WeatherEntity> findByAggregationMethod(String aggregationMethod);

    /**
     * Find all entities matching a device class.
     */
    List<WeatherEntity> findByDeviceClass(String deviceClass);

    /**
     * Check if an entity exists by ID.
     */
    boolean existsByEntityId(String entityId);
}
