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
package com.owl.adapter.metar;

import com.owl.core.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for METAR adapter.
 */
class MetarAdapterTest {

    private MetarAdapter createAdapter() {
        MetarConfiguration config = new MetarConfiguration();
        config.setStationId("EBBR");
        return new MetarAdapter(new NoOpMessageBus(), new NoOpMetricsRegistry(), config);
    }

    @Test
    void adapterHasCorrectIdentity() {
        MetarAdapter adapter = createAdapter();

        assertEquals("metar-http", adapter.getName());
        assertEquals("METAR Weather Adapter", adapter.getDisplayName());
        assertEquals("1.0.0", adapter.getVersion());
    }

    @Test
    void adapterProvidesExpectedEntities() {
        MetarAdapter adapter = createAdapter();

        List<EntityDefinition> entities = adapter.getProvidedEntities();

        assertNotNull(entities);
        assertFalse(entities.isEmpty());

        assertTrue(entities.stream()
                        .anyMatch(e -> e.getEntityId().equals("sensor.metar_temperature")),
                "Should provide temperature entity");

        assertTrue(entities.stream()
                        .anyMatch(e -> e.getEntityId().equals("sensor.metar_pressure")),
                "Should provide pressure entity");

        assertTrue(entities.stream()
                        .anyMatch(e -> e.getEntityId().equals("sensor.metar_wind_speed")),
                "Should provide wind speed entity");
    }

    @Test
    void adapterEntitiesHaveCorrectSource() {
        MetarAdapter adapter = createAdapter();

        List<EntityDefinition> entities = adapter.getProvidedEntities();

        for (EntityDefinition entity : entities) {
            assertEquals("metar-http", entity.getSource(),
                    "Entity " + entity.getEntityId() + " should have correct source");
        }
    }

    @Test
    void adapterInitiallyUnhealthy() {
        MetarAdapter adapter = createAdapter();

        AdapterHealth health = adapter.getHealth();

        assertEquals(AdapterHealth.Status.UNHEALTHY, health.getStatus());
    }

    @Test
    void adapterDoesNotSupportRecovery() {
        MetarAdapter adapter = createAdapter();

        assertFalse(adapter.supportsRecovery());
    }

    // Minimal test doubles

    static class NoOpMessageBus implements MessageBus {
        @Override public void publish(WeatherEvent event) {}
        @Override public void publishBatch(List<? extends WeatherEvent> events) {}
        @Override public <T extends WeatherEvent> void subscribe(Class<T> eventType, Consumer<T> consumer) {}
    }

    static class NoOpMetricsRegistry implements MetricsRegistry {
        @Override public void incrementCounter(String name) {}
        @Override public void incrementCounter(String name, long amount) {}
        @Override public void incrementCounter(String name, Map<String, String> tags) {}
        @Override public void recordGauge(String name, double value) {}
        @Override public void registerGauge(String name, java.util.function.Supplier<Number> supplier) {}
        @Override public void recordTiming(String name, long durationMs) {}
        @Override public void recordDistribution(String name, double value) {}
    }
}
