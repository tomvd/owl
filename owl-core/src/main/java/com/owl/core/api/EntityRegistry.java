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

import java.util.List;
import java.util.Optional;

/**
 * Registry for looking up entity metadata.
 * <p>
 * Provides access to entity definitions registered by adapters.
 */
public interface EntityRegistry {

    /**
     * Get entity definition by ID.
     *
     * @param entityId entity ID
     * @return entity definition or empty if not found
     */
    Optional<EntityDefinition> getEntity(String entityId);

    /**
     * Get all entities from a specific source.
     *
     * @param source source name
     * @return list of entities
     */
    List<EntityDefinition> getEntitiesBySource(String source);

    /**
     * Get all registered entities.
     *
     * @return all entities
     */
    List<EntityDefinition> getAllEntities();

    /**
     * Register a new entity definition.
     * <p>
     * Called by adapters during initialization.
     *
     * @param entity entity definition to register
     */
    void register(EntityDefinition entity);

    /**
     * Register multiple entity definitions.
     *
     * @param entities entity definitions to register
     */
    default void registerAll(List<EntityDefinition> entities) {
        for (EntityDefinition entity : entities) {
            register(entity);
        }
    }

    /**
     * Check if an entity is registered.
     *
     * @param entityId entity ID
     * @return true if registered
     */
    default boolean isRegistered(String entityId) {
        return getEntity(entityId).isPresent();
    }
}
