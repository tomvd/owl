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
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and manages Owl services using Java's ServiceLoader mechanism.
 * <p>
 * Discovers services from:
 * <ul>
 *   <li>The application classpath</li>
 *   <li>JAR files in the plugins directory</li>
 * </ul>
 */
@Singleton
@Context
public class ServiceLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceLoader.class);

    private final ServiceContextFactory contextFactory;
    private final Map<String, ManagedService> services = new ConcurrentHashMap<>();

    @Value("${owl.plugins.directory:plugins}")
    private String pluginsDirectory;

    public ServiceLoader(ServiceContextFactory contextFactory) {
        this.contextFactory = contextFactory;
    }

    @EventListener
    public void onStartup(ServerStartupEvent event) {
        LOG.info("Starting service discovery...");
        discoverAndLoadServices();
        startEnabledServices();
    }

    @EventListener
    public void onShutdown(ServerShutdownEvent event) {
        LOG.info("Stopping all services...");
        stopAllServices();
    }

    /**
     * Discover all service providers and create services.
     */
    private void discoverAndLoadServices() {
        List<ServiceProvider> providers = new ArrayList<>();

        // Load from classpath
        java.util.ServiceLoader<ServiceProvider> classpathProviders =
                java.util.ServiceLoader.load(ServiceProvider.class);
        classpathProviders.forEach(providers::add);
        LOG.info("Found {} services on classpath", providers.size());

        // Load from plugins directory
        File pluginsDir = new File(pluginsDirectory);
        if (pluginsDir.exists() && pluginsDir.isDirectory()) {
            List<ServiceProvider> pluginProviders = loadFromPluginsDirectory(pluginsDir);
            providers.addAll(pluginProviders);
            LOG.info("Found {} services in plugins directory", pluginProviders.size());
        } else {
            LOG.debug("Plugins directory not found or not a directory: {}", pluginsDirectory);
        }

        // Create services from providers
        for (ServiceProvider provider : providers) {
            try {
                ProviderMetadata metadata = provider.getMetadata();
                LOG.info("Loading service: {}", metadata);

                OwlService service = provider.createService();
                String serviceName = service.getName();

                if (services.containsKey(serviceName)) {
                    LOG.warn("Duplicate service name '{}', skipping", serviceName);
                    continue;
                }

                services.put(serviceName, new ManagedService(service, metadata));
                LOG.info("Registered service: {} ({})", service.getDisplayName(), serviceName);

            } catch (Exception e) {
                LOG.error("Failed to create service from provider: {}", provider.getClass().getName(), e);
            }
        }

        LOG.info("Total services loaded: {}", services.size());
    }

    /**
     * Load service providers from JAR files in the plugins directory.
     */
    private List<ServiceProvider> loadFromPluginsDirectory(File pluginsDir) {
        List<ServiceProvider> providers = new ArrayList<>();
        File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            return providers;
        }

        try {
            URL[] urls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                urls[i] = jarFiles[i].toURI().toURL();
                LOG.debug("Adding plugin JAR: {}", jarFiles[i].getName());
            }

            URLClassLoader pluginClassLoader = new URLClassLoader(urls, getClass().getClassLoader());
            java.util.ServiceLoader<ServiceProvider> loader =
                    java.util.ServiceLoader.load(ServiceProvider.class, pluginClassLoader);
            loader.forEach(providers::add);

        } catch (Exception e) {
            LOG.error("Failed to load services from plugins directory", e);
        }

        return providers;
    }

    /**
     * Start all enabled services.
     */
    private void startEnabledServices() {
        for (ManagedService managed : services.values()) {
            OwlService service = managed.service();
            String serviceName = service.getName();

            try {
                ServiceContext context = contextFactory.createContext(serviceName);

                if (!context.getConfiguration().isEnabled()) {
                    LOG.info("Service '{}' is disabled in configuration", serviceName);
                    continue;
                }

                // Start the service
                LOG.info("Starting service: {}", serviceName);
                service.start(context);
                managed.setRunning(true);
                LOG.info("Service '{}' started successfully", serviceName);

            } catch (Exception e) {
                LOG.error("Failed to start service: {}", serviceName, e);
            }
        }
    }

    /**
     * Stop all running services.
     */
    private void stopAllServices() {
        for (ManagedService managed : services.values()) {
            if (managed.isRunning()) {
                try {
                    LOG.info("Stopping service: {}", managed.service().getName());
                    managed.service().stop();
                    managed.setRunning(false);
                } catch (Exception e) {
                    LOG.error("Error stopping service: {}", managed.service().getName(), e);
                }
            }
        }
    }

    /**
     * Get all registered services.
     *
     * @return unmodifiable collection of services
     */
    public Collection<OwlService> getServices() {
        return services.values().stream()
                .map(ManagedService::service)
                .toList();
    }

    /**
     * Get a service by name.
     *
     * @param name service name
     * @return service or empty if not found
     */
    public Optional<OwlService> getService(String name) {
        ManagedService managed = services.get(name);
        return managed != null ? Optional.of(managed.service()) : Optional.empty();
    }

    /**
     * Get health status of all services.
     *
     * @return map of service name to health status
     */
    public Map<String, ServiceHealth> getHealthStatuses() {
        Map<String, ServiceHealth> statuses = new LinkedHashMap<>();
        for (ManagedService managed : services.values()) {
            try {
                statuses.put(managed.service().getName(), managed.service().getHealth());
            } catch (Exception e) {
                statuses.put(managed.service().getName(),
                        ServiceHealth.unhealthy("Error getting health: " + e.getMessage()));
            }
        }
        return statuses;
    }

    /**
     * Check if a service is running.
     *
     * @param name service name
     * @return true if running
     */
    public boolean isServiceRunning(String name) {
        ManagedService managed = services.get(name);
        return managed != null && managed.isRunning();
    }

    /**
     * Internal class to track service state.
     */
    private static class ManagedService {
        private final OwlService service;
        private final ProviderMetadata metadata;
        private volatile boolean running = false;

        ManagedService(OwlService service, ProviderMetadata metadata) {
            this.service = service;
            this.metadata = metadata;
        }

        OwlService service() {
            return service;
        }

        ProviderMetadata metadata() {
            return metadata;
        }

        boolean isRunning() {
            return running;
        }

        void setRunning(boolean running) {
            this.running = running;
        }
    }
}
