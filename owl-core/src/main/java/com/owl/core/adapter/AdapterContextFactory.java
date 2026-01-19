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
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for creating AdapterContext instances.
 */
@Singleton
public class AdapterContextFactory {

    private final MessageBus messageBus;
    private final EntityRegistry entityRegistry;
    private final MetricsRegistry metricsRegistry;
    private final Environment environment;

    public AdapterContextFactory(
            MessageBus messageBus,
            EntityRegistry entityRegistry,
            MetricsRegistry metricsRegistry,
            ApplicationContext applicationContext) {
        this.messageBus = messageBus;
        this.entityRegistry = entityRegistry;
        this.metricsRegistry = metricsRegistry;
        this.environment = applicationContext.getEnvironment();
    }

    /**
     * Create an AdapterContext for the given adapter.
     *
     * @param adapterName adapter name
     * @return adapter context
     */
    public AdapterContext createContext(String adapterName) {
        return new AdapterContextImpl(
                adapterName,
                messageBus,
                entityRegistry,
                metricsRegistry,
                createConfiguration(adapterName)
        );
    }

    /**
     * Create configuration for the given adapter.
     */
    private AdapterConfiguration createConfiguration(String adapterName) {
        return new AdapterConfigurationImpl(adapterName, environment);
    }

    /**
     * Implementation of AdapterContext.
     */
    private static class AdapterContextImpl implements AdapterContext {

        private final String adapterName;
        private final MessageBus messageBus;
        private final EntityRegistry entityRegistry;
        private final MetricsRegistry metricsRegistry;
        private final AdapterConfiguration configuration;
        private final Logger logger;

        AdapterContextImpl(
                String adapterName,
                MessageBus messageBus,
                EntityRegistry entityRegistry,
                MetricsRegistry metricsRegistry,
                AdapterConfiguration configuration) {
            this.adapterName = adapterName;
            this.messageBus = messageBus;
            this.entityRegistry = entityRegistry;
            this.metricsRegistry = metricsRegistry;
            this.configuration = configuration;
            this.logger = LoggerFactory.getLogger("owl.adapter." + adapterName);
        }

        @Override
        public MessageBus getMessageBus() {
            return messageBus;
        }

        @Override
        public AdapterConfiguration getConfiguration() {
            return configuration;
        }

        @Override
        public Logger getLogger() {
            return logger;
        }

        @Override
        public EntityRegistry getEntityRegistry() {
            return entityRegistry;
        }

        @Override
        public MetricsRegistry getMetrics() {
            return metricsRegistry;
        }
    }

    /**
     * Implementation of AdapterConfiguration.
     */
    private static class AdapterConfigurationImpl implements AdapterConfiguration {

        private final String adapterName;
        private final Environment environment;
        private final String prefix;

        AdapterConfigurationImpl(String adapterName, Environment environment) {
            this.adapterName = adapterName;
            this.environment = environment;
            this.prefix = "owl.adapters." + adapterName + ".";
        }

        @Override
        public boolean isEnabled() {
            return environment.getProperty(prefix + "enabled", Boolean.class).orElse(false);
        }

        @Override
        public String getAdapterName() {
            return adapterName;
        }

        @Override
        public Optional<String> getString(String key) {
            return environment.getProperty(prefix + key, String.class);
        }

        @Override
        public Optional<Integer> getInt(String key) {
            return environment.getProperty(prefix + key, Integer.class);
        }

        @Override
        public Optional<Long> getLong(String key) {
            return environment.getProperty(prefix + key, Long.class);
        }

        @Override
        public Optional<Boolean> getBoolean(String key) {
            return environment.getProperty(prefix + key, Boolean.class);
        }

        @Override
        public Optional<Double> getDouble(String key) {
            return environment.getProperty(prefix + key, Double.class);
        }

        @Override
        public Map<String, Object> asMap() {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> adapterConfig = environment
                    .getProperty("owl.adapters." + adapterName, Map.class)
                    .orElse(Map.of());
            result.putAll(adapterConfig);
            return result;
        }
    }
}
