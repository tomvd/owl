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
package com.owl.core.entity;

import com.owl.core.api.EntityDefinition;
import com.owl.core.api.EntityRegistry;
import com.owl.core.persistence.repository.WeatherEntityRepository;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory entity registry implementation.
 * <p>
 * Maintains a registry of all entity definitions and synchronizes
 * with the database on startup.
 */
@Singleton
@Context
public class EntityRegistryImpl implements EntityRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(EntityRegistryImpl.class);

    private final Map<String, EntityDefinition> entities = new ConcurrentHashMap<>();
    private final WeatherEntityRepository repository;

    public EntityRegistryImpl(WeatherEntityRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initialize() {
        LOG.info("Initializing entity registry");
        loadFromDatabase();
    }

    /**
     * Load existing entities from the database.
     */
    private void loadFromDatabase() {
        try {
            repository.findAll().forEach(dbEntity -> {
                EntityDefinition definition = mapFromDbEntity(dbEntity);
                entities.put(definition.getEntityId(), definition);
            });
            LOG.info("Loaded {} entities from database", entities.size());
        } catch (Exception e) {
            LOG.warn("Failed to load entities from database: {}", e.getMessage());
        }
    }

    @Override
    public Optional<EntityDefinition> getEntity(String entityId) {
        return Optional.ofNullable(entities.get(entityId));
    }

    @Override
    public List<EntityDefinition> getEntitiesBySource(String source) {
        return entities.values().stream()
                .filter(e -> source.equals(e.getSource()))
                .collect(Collectors.toList());
    }

    @Override
    public List<EntityDefinition> getAllEntities() {
        return new ArrayList<>(entities.values());
    }

    @Override
    public void register(EntityDefinition entity) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(entity.getEntityId(), "entityId");

        String entityId = entity.getEntityId();

        if (entities.containsKey(entityId)) {
            LOG.debug("Entity already registered: {}", entityId);
            return;
        }

        entities.put(entityId, entity);
        LOG.info("Registered entity: {}", entityId);

        // Persist to database
        persistEntity(entity);
    }

    /**
     * Persist entity definition to database.
     */
    private void persistEntity(EntityDefinition entity) {
        try {
            if (!repository.existsByEntityId(entity.getEntityId())) {
                com.owl.core.persistence.entity.WeatherEntity dbEntity = mapToDbEntity(entity);
                repository.save(dbEntity);
                LOG.debug("Persisted entity to database: {}", entity.getEntityId());
            }
        } catch (Exception e) {
            LOG.warn("Failed to persist entity {}: {}", entity.getEntityId(), e.getMessage());
        }
    }

    /**
     * Map from database entity to API definition.
     */
    private EntityDefinition mapFromDbEntity(com.owl.core.persistence.entity.WeatherEntity dbEntity) {
        return EntityDefinition.builder()
                .entityId(dbEntity.getEntityId())
                .friendlyName(dbEntity.getFriendlyName())
                .source(dbEntity.getSource())
                .unit(dbEntity.getUnitOfMeasurement())
                .deviceClass(dbEntity.getDeviceClass())
                .stateClass(dbEntity.getStateClass())
                .aggregation(parseAggregationMethod(dbEntity.getAggregationMethod()))
                .build();
    }

    /**
     * Map from API definition to database entity.
     */
    private com.owl.core.persistence.entity.WeatherEntity mapToDbEntity(EntityDefinition entity) {
        com.owl.core.persistence.entity.WeatherEntity dbEntity =
                new com.owl.core.persistence.entity.WeatherEntity();
        dbEntity.setEntityId(entity.getEntityId());
        dbEntity.setFriendlyName(entity.getFriendlyName());
        dbEntity.setSource(entity.getSource());
        dbEntity.setUnitOfMeasurement(entity.getUnitOfMeasurement());
        dbEntity.setDeviceClass(entity.getDeviceClass());
        dbEntity.setStateClass(entity.getStateClass());
        dbEntity.setAggregationMethod(entity.getAggregationMethod().name().toLowerCase());
        return dbEntity;
    }

    /**
     * Parse aggregation method from string.
     */
    private com.owl.core.api.AggregationMethod parseAggregationMethod(String method) {
        if (method == null) {
            return com.owl.core.api.AggregationMethod.MEAN;
        }
        try {
            return com.owl.core.api.AggregationMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return com.owl.core.api.AggregationMethod.MEAN;
        }
    }
}
