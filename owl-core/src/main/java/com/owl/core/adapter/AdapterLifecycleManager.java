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
package com.owl.core.adapter;

import com.owl.core.api.*;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages weather adapter lifecycle and entity registration.
 * <p>
 * Replaces the old ServiceLoader-based AdapterLoader. All adapters are now
 * discovered via Micronaut DI (injected as {@code List<WeatherAdapter>}).
 * Entity registration happens automatically at startup.
 */
@Singleton
@Context
public class AdapterLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(AdapterLifecycleManager.class);

    private final List<WeatherAdapter> adapters;
    private final EntityRegistry entityRegistry;

    public AdapterLifecycleManager(List<WeatherAdapter> adapters, EntityRegistry entityRegistry) {
        this.adapters = adapters;
        this.entityRegistry = entityRegistry;
    }

    @PostConstruct
    void registerEntities() {
        LOG.info("Registering entities from {} adapter(s)", adapters.size());
        for (WeatherAdapter adapter : adapters) {
            List<EntityDefinition> entities = adapter.getProvidedEntities();
            entityRegistry.registerAll(entities);
            LOG.info("Registered {} entities from adapter '{}'", entities.size(), adapter.getName());
        }
    }

    /**
     * Get all registered adapters.
     */
    public Collection<WeatherAdapter> getAdapters() {
        return Collections.unmodifiableList(adapters);
    }

    /**
     * Get an adapter by name.
     */
    public Optional<WeatherAdapter> getAdapter(String name) {
        return adapters.stream()
                .filter(a -> a.getName().equals(name))
                .findFirst();
    }

    /**
     * Get health status of all adapters.
     */
    public Map<String, AdapterHealth> getHealthStatuses() {
        Map<String, AdapterHealth> statuses = new LinkedHashMap<>();
        for (WeatherAdapter adapter : adapters) {
            try {
                statuses.put(adapter.getName(), adapter.getHealth());
            } catch (Exception e) {
                statuses.put(adapter.getName(),
                        AdapterHealth.unhealthy("Error getting health: " + e.getMessage()));
            }
        }
        return statuses;
    }
}
