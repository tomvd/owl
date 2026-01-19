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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for METAR adapter.
 */
class MetarAdapterTest {

    @Test
    void providerCreatesAdapter() {
        MetarAdapterProvider provider = new MetarAdapterProvider();

        WeatherAdapter adapter = provider.createAdapter();

        assertNotNull(adapter);
        assertInstanceOf(MetarAdapter.class, adapter);
    }

    @Test
    void providerHasCorrectMetadata() {
        MetarAdapterProvider provider = new MetarAdapterProvider();

        ProviderMetadata metadata = provider.getMetadata();

        assertEquals("metar-http", metadata.id());
        assertEquals("METAR Weather Adapter", metadata.name());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void adapterHasCorrectIdentity() {
        MetarAdapter adapter = new MetarAdapter();

        assertEquals("metar-http", adapter.getName());
        assertEquals("METAR Weather Adapter", adapter.getDisplayName());
        assertEquals("1.0.0", adapter.getVersion());
    }

    @Test
    void adapterProvidesExpectedEntities() {
        MetarAdapter adapter = new MetarAdapter();

        List<EntityDefinition> entities = adapter.getProvidedEntities();

        assertNotNull(entities);
        assertFalse(entities.isEmpty());

        // Check for temperature entity
        assertTrue(entities.stream()
                        .anyMatch(e -> e.getEntityId().equals("sensor.metar_temperature")),
                "Should provide temperature entity");

        // Check for pressure entity
        assertTrue(entities.stream()
                        .anyMatch(e -> e.getEntityId().equals("sensor.metar_pressure")),
                "Should provide pressure entity");

        // Check for wind speed entity
        assertTrue(entities.stream()
                        .anyMatch(e -> e.getEntityId().equals("sensor.metar_wind_speed")),
                "Should provide wind speed entity");
    }

    @Test
    void adapterEntitiesHaveCorrectSource() {
        MetarAdapter adapter = new MetarAdapter();

        List<EntityDefinition> entities = adapter.getProvidedEntities();

        for (EntityDefinition entity : entities) {
            assertEquals("metar-http", entity.getSource(),
                    "Entity " + entity.getEntityId() + " should have correct source");
        }
    }

    @Test
    void adapterInitiallyUnhealthy() {
        MetarAdapter adapter = new MetarAdapter();

        AdapterHealth health = adapter.getHealth();

        assertEquals(AdapterHealth.Status.UNHEALTHY, health.getStatus());
    }

    @Test
    void adapterDoesNotSupportRecovery() {
        MetarAdapter adapter = new MetarAdapter();

        assertFalse(adapter.supportsRecovery());
    }
}
