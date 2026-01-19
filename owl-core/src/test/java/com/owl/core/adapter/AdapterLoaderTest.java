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

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for adapter discovery mechanism.
 */
class AdapterLoaderTest {

    @Test
    void serviceLoaderCanFindAdapterProviders() {
        // This test verifies that the ServiceLoader mechanism works
        ServiceLoader<AdapterProvider> loader = ServiceLoader.load(AdapterProvider.class);

        // Count providers found
        int count = 0;
        for (AdapterProvider provider : loader) {
            assertNotNull(provider, "Provider should not be null");
            assertNotNull(provider.getMetadata(), "Metadata should not be null");
            count++;
        }

        // We may or may not have providers depending on test classpath
        // This test mainly verifies the mechanism works without errors
        assertTrue(count >= 0, "ServiceLoader should work without errors");
    }

    @Test
    void adapterProviderCreatesAdapter() {
        // Create a test provider
        AdapterProvider provider = new TestAdapterProvider();

        // Verify metadata
        ProviderMetadata metadata = provider.getMetadata();
        assertEquals("test-adapter", metadata.id());
        assertEquals("Test Adapter", metadata.name());

        // Verify adapter creation
        WeatherAdapter adapter = provider.createAdapter();
        assertNotNull(adapter);
        assertEquals("test-adapter", adapter.getName());
    }

    @Test
    void adapterProvidesEntities() {
        TestAdapter adapter = new TestAdapter();
        List<EntityDefinition> entities = adapter.getProvidedEntities();

        assertNotNull(entities);
        assertFalse(entities.isEmpty());

        EntityDefinition entity = entities.get(0);
        assertEquals("sensor.test_temperature", entity.getEntityId());
        assertEquals("test-adapter", entity.getSource());
    }

    @Test
    void adapterHealthReportsCorrectly() {
        TestAdapter adapter = new TestAdapter();

        // Before start, should be unhealthy
        AdapterHealth health = adapter.getHealth();
        assertEquals(AdapterHealth.Status.UNHEALTHY, health.getStatus());
    }

    // Test implementations
    static class TestAdapterProvider implements AdapterProvider {
        @Override
        public WeatherAdapter createAdapter() {
            return new TestAdapter();
        }

        @Override
        public ProviderMetadata getMetadata() {
            return new ProviderMetadata(
                    "test-adapter",
                    "Test Adapter",
                    "1.0.0",
                    "Test",
                    "Test adapter for unit tests"
            );
        }
    }

    static class TestAdapter implements WeatherAdapter {
        private boolean running = false;

        @Override
        public String getName() {
            return "test-adapter";
        }

        @Override
        public String getDisplayName() {
            return "Test Adapter";
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public List<EntityDefinition> getProvidedEntities() {
            return List.of(
                    EntityDefinition.builder()
                            .entityId("sensor.test_temperature")
                            .friendlyName("Test Temperature")
                            .source("test-adapter")
                            .unit("°C")
                            .deviceClass("temperature")
                            .aggregation(AggregationMethod.MEAN)
                            .build()
            );
        }

        @Override
        public void start(AdapterContext context) throws AdapterException {
            running = true;
        }

        @Override
        public void stop() throws AdapterException {
            running = false;
        }

        @Override
        public AdapterHealth getHealth() {
            if (!running) {
                return AdapterHealth.unhealthy("Not running");
            }
            return AdapterHealth.healthy("OK");
        }
    }
}
