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
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AdapterLifecycleManager entity registration and adapter lookup.
 */
class AdapterLoaderTest {

    @Test
    void registersEntitiesFromAllAdapters() {
        TestEntityRegistry registry = new TestEntityRegistry();
        TestAdapter adapter1 = new TestAdapter("adapter-1", List.of(
                EntityDefinition.builder()
                        .entityId("sensor.test_temp")
                        .friendlyName("Test Temperature")
                        .source("adapter-1")
                        .unit("°C")
                        .deviceClass("temperature")
                        .aggregation(AggregationMethod.MEAN)
                        .build()
        ));
        TestAdapter adapter2 = new TestAdapter("adapter-2", List.of(
                EntityDefinition.builder()
                        .entityId("sensor.test_humidity")
                        .friendlyName("Test Humidity")
                        .source("adapter-2")
                        .unit("%")
                        .deviceClass("humidity")
                        .aggregation(AggregationMethod.MEAN)
                        .build()
        ));

        AdapterLifecycleManager manager = new AdapterLifecycleManager(
                List.of(adapter1, adapter2), registry);
        manager.registerEntities();

        assertEquals(2, registry.getAllEntities().size());
        assertTrue(registry.isRegistered("sensor.test_temp"));
        assertTrue(registry.isRegistered("sensor.test_humidity"));
    }

    @Test
    void getAdapterByName() {
        TestEntityRegistry registry = new TestEntityRegistry();
        TestAdapter adapter = new TestAdapter("test-adapter", List.of());

        AdapterLifecycleManager manager = new AdapterLifecycleManager(
                List.of(adapter), registry);

        assertTrue(manager.getAdapter("test-adapter").isPresent());
        assertEquals("test-adapter", manager.getAdapter("test-adapter").get().getName());
        assertTrue(manager.getAdapter("nonexistent").isEmpty());
    }

    @Test
    void getAdaptersReturnsAll() {
        TestEntityRegistry registry = new TestEntityRegistry();
        TestAdapter a1 = new TestAdapter("a1", List.of());
        TestAdapter a2 = new TestAdapter("a2", List.of());

        AdapterLifecycleManager manager = new AdapterLifecycleManager(
                List.of(a1, a2), registry);

        assertEquals(2, manager.getAdapters().size());
    }

    @Test
    void healthStatusesCollected() {
        TestEntityRegistry registry = new TestEntityRegistry();
        TestAdapter adapter = new TestAdapter("test-adapter", List.of());

        AdapterLifecycleManager manager = new AdapterLifecycleManager(
                List.of(adapter), registry);

        Map<String, AdapterHealth> statuses = manager.getHealthStatuses();
        assertEquals(1, statuses.size());
        assertEquals(AdapterHealth.Status.UNHEALTHY, statuses.get("test-adapter").getStatus());
    }

    @Test
    void adapterProvidesEntities() {
        TestAdapter adapter = new TestAdapter("test-adapter", List.of(
                EntityDefinition.builder()
                        .entityId("sensor.test_temperature")
                        .friendlyName("Test Temperature")
                        .source("test-adapter")
                        .unit("°C")
                        .deviceClass("temperature")
                        .aggregation(AggregationMethod.MEAN)
                        .build()
        ));

        List<EntityDefinition> entities = adapter.getProvidedEntities();
        assertNotNull(entities);
        assertFalse(entities.isEmpty());
        assertEquals("sensor.test_temperature", entities.get(0).getEntityId());
        assertEquals("test-adapter", entities.get(0).getSource());
    }

    @Test
    void adapterHealthReportsCorrectly() {
        TestAdapter adapter = new TestAdapter("test-adapter", List.of());
        AdapterHealth health = adapter.getHealth();
        assertEquals(AdapterHealth.Status.UNHEALTHY, health.getStatus());
    }

    // Test implementations

    static class TestAdapter implements WeatherAdapter {
        private final String name;
        private final List<EntityDefinition> entities;

        TestAdapter(String name, List<EntityDefinition> entities) {
            this.name = name;
            this.entities = entities;
        }

        @Override public String getName() { return name; }
        @Override public String getDisplayName() { return "Test " + name; }
        @Override public String getVersion() { return "1.0.0"; }
        @Override public List<EntityDefinition> getProvidedEntities() { return entities; }

        @Override
        public AdapterHealth getHealth() {
            return AdapterHealth.unhealthy("Not running");
        }
    }

    static class TestEntityRegistry implements EntityRegistry {
        private final Map<String, EntityDefinition> entities = new LinkedHashMap<>();

        @Override
        public Optional<EntityDefinition> getEntity(String entityId) {
            return Optional.ofNullable(entities.get(entityId));
        }

        @Override
        public List<EntityDefinition> getEntitiesBySource(String source) {
            return entities.values().stream()
                    .filter(e -> source.equals(e.getSource()))
                    .toList();
        }

        @Override
        public List<EntityDefinition> getAllEntities() {
            return new ArrayList<>(entities.values());
        }

        @Override
        public void register(EntityDefinition entity) {
            entities.put(entity.getEntityId(), entity);
        }
    }
}
