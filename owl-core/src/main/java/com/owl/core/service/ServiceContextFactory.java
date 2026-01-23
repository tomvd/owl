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
package com.owl.core.service;

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
 * Factory for creating ServiceContext instances.
 */
@Singleton
public class ServiceContextFactory {

    private final MessageBus messageBus;
    private final EntityRegistry entityRegistry;
    private final MetricsRegistry metricsRegistry;
    private final Environment environment;

    public ServiceContextFactory(
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
     * Create a ServiceContext for the given service.
     *
     * @param serviceName service name
     * @return service context
     */
    public ServiceContext createContext(String serviceName) {
        return new ServiceContextImpl(
                serviceName,
                messageBus,
                entityRegistry,
                metricsRegistry,
                createConfiguration(serviceName)
        );
    }

    /**
     * Create configuration for the given service.
     */
    private ServiceConfiguration createConfiguration(String serviceName) {
        return new ServiceConfigurationImpl(serviceName, environment);
    }

    /**
     * Implementation of ServiceContext.
     */
    private static class ServiceContextImpl implements ServiceContext {

        private final String serviceName;
        private final MessageBus messageBus;
        private final EntityRegistry entityRegistry;
        private final MetricsRegistry metricsRegistry;
        private final ServiceConfiguration configuration;
        private final Logger logger;

        ServiceContextImpl(
                String serviceName,
                MessageBus messageBus,
                EntityRegistry entityRegistry,
                MetricsRegistry metricsRegistry,
                ServiceConfiguration configuration) {
            this.serviceName = serviceName;
            this.messageBus = messageBus;
            this.entityRegistry = entityRegistry;
            this.metricsRegistry = metricsRegistry;
            this.configuration = configuration;
            this.logger = LoggerFactory.getLogger("owl.service." + serviceName);
        }

        @Override
        public MessageBus getMessageBus() {
            return messageBus;
        }

        @Override
        public ServiceConfiguration getConfiguration() {
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
     * Implementation of ServiceConfiguration.
     */
    private static class ServiceConfigurationImpl implements ServiceConfiguration {

        private final String serviceName;
        private final Environment environment;
        private final String prefix;

        ServiceConfigurationImpl(String serviceName, Environment environment) {
            this.serviceName = serviceName;
            this.environment = environment;
            this.prefix = "owl.services." + serviceName + ".";
        }

        @Override
        public boolean isEnabled() {
            return environment.getProperty(prefix + "enabled", Boolean.class).orElse(false);
        }

        @Override
        public String getServiceName() {
            return serviceName;
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
            Map<String, Object> serviceConfig = environment
                    .getProperty("owl.services." + serviceName, Map.class)
                    .orElse(Map.of());
            result.putAll(serviceConfig);
            return result;
        }
    }
}
