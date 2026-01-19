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
 * Loads and manages weather adapters using Java's ServiceLoader mechanism.
 * <p>
 * Discovers adapters from:
 * <ul>
 *   <li>The application classpath</li>
 *   <li>JAR files in the plugins directory</li>
 * </ul>
 */
@Singleton
@Context
public class AdapterLoader {

    private static final Logger LOG = LoggerFactory.getLogger(AdapterLoader.class);

    private final AdapterContextFactory contextFactory;
    private final EntityRegistry entityRegistry;
    private final Map<String, ManagedAdapter> adapters = new ConcurrentHashMap<>();

    @Value("${owl.plugins.directory:plugins}")
    private String pluginsDirectory;

    public AdapterLoader(AdapterContextFactory contextFactory, EntityRegistry entityRegistry) {
        this.contextFactory = contextFactory;
        this.entityRegistry = entityRegistry;
    }

    @EventListener
    public void onStartup(ServerStartupEvent event) {
        LOG.info("Starting adapter discovery...");
        discoverAndLoadAdapters();
        startEnabledAdapters();
    }

    @EventListener
    public void onShutdown(ServerShutdownEvent event) {
        LOG.info("Stopping all adapters...");
        stopAllAdapters();
    }

    /**
     * Discover all adapter providers and create adapters.
     */
    private void discoverAndLoadAdapters() {
        List<AdapterProvider> providers = new ArrayList<>();

        // Load from classpath
        ServiceLoader<AdapterProvider> classpathProviders = ServiceLoader.load(AdapterProvider.class);
        classpathProviders.forEach(providers::add);
        LOG.info("Found {} adapters on classpath", providers.size());

        // Load from plugins directory
        File pluginsDir = new File(pluginsDirectory);
        if (pluginsDir.exists() && pluginsDir.isDirectory()) {
            List<AdapterProvider> pluginProviders = loadFromPluginsDirectory(pluginsDir);
            providers.addAll(pluginProviders);
            LOG.info("Found {} adapters in plugins directory", pluginProviders.size());
        } else {
            LOG.debug("Plugins directory not found or not a directory: {}", pluginsDirectory);
        }

        // Create adapters from providers
        for (AdapterProvider provider : providers) {
            try {
                ProviderMetadata metadata = provider.getMetadata();
                LOG.info("Loading adapter: {}", metadata);

                WeatherAdapter adapter = provider.createAdapter();
                String adapterName = adapter.getName();

                if (adapters.containsKey(adapterName)) {
                    LOG.warn("Duplicate adapter name '{}', skipping", adapterName);
                    continue;
                }

                adapters.put(adapterName, new ManagedAdapter(adapter, metadata));
                LOG.info("Registered adapter: {} ({})", adapter.getDisplayName(), adapterName);

            } catch (Exception e) {
                LOG.error("Failed to create adapter from provider: {}", provider.getClass().getName(), e);
            }
        }

        LOG.info("Total adapters loaded: {}", adapters.size());
    }

    /**
     * Load adapter providers from JAR files in the plugins directory.
     */
    private List<AdapterProvider> loadFromPluginsDirectory(File pluginsDir) {
        List<AdapterProvider> providers = new ArrayList<>();
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
            ServiceLoader<AdapterProvider> loader = ServiceLoader.load(AdapterProvider.class, pluginClassLoader);
            loader.forEach(providers::add);

        } catch (Exception e) {
            LOG.error("Failed to load adapters from plugins directory", e);
        }

        return providers;
    }

    /**
     * Start all enabled adapters.
     */
    private void startEnabledAdapters() {
        for (ManagedAdapter managed : adapters.values()) {
            WeatherAdapter adapter = managed.adapter();
            String adapterName = adapter.getName();

            try {
                AdapterContext context = contextFactory.createContext(adapterName);

                if (!context.getConfiguration().isEnabled()) {
                    LOG.info("Adapter '{}' is disabled in configuration", adapterName);
                    continue;
                }

                // Register entities
                List<EntityDefinition> entities = adapter.getProvidedEntities();
                entityRegistry.registerAll(entities);
                LOG.info("Registered {} entities from adapter '{}'", entities.size(), adapterName);

                // Start the adapter
                LOG.info("Starting adapter: {}", adapterName);
                adapter.start(context);
                managed.setRunning(true);
                LOG.info("Adapter '{}' started successfully", adapterName);

            } catch (Exception e) {
                LOG.error("Failed to start adapter: {}", adapterName, e);
            }
        }
    }

    /**
     * Stop all running adapters.
     */
    private void stopAllAdapters() {
        for (ManagedAdapter managed : adapters.values()) {
            if (managed.isRunning()) {
                try {
                    LOG.info("Stopping adapter: {}", managed.adapter().getName());
                    managed.adapter().stop();
                    managed.setRunning(false);
                } catch (Exception e) {
                    LOG.error("Error stopping adapter: {}", managed.adapter().getName(), e);
                }
            }
        }
    }

    /**
     * Get all registered adapters.
     *
     * @return unmodifiable collection of adapters
     */
    public Collection<WeatherAdapter> getAdapters() {
        return adapters.values().stream()
                .map(ManagedAdapter::adapter)
                .toList();
    }

    /**
     * Get an adapter by name.
     *
     * @param name adapter name
     * @return adapter or empty if not found
     */
    public Optional<WeatherAdapter> getAdapter(String name) {
        ManagedAdapter managed = adapters.get(name);
        return managed != null ? Optional.of(managed.adapter()) : Optional.empty();
    }

    /**
     * Get health status of all adapters.
     *
     * @return map of adapter name to health status
     */
    public Map<String, AdapterHealth> getHealthStatuses() {
        Map<String, AdapterHealth> statuses = new LinkedHashMap<>();
        for (ManagedAdapter managed : adapters.values()) {
            try {
                statuses.put(managed.adapter().getName(), managed.adapter().getHealth());
            } catch (Exception e) {
                statuses.put(managed.adapter().getName(),
                        AdapterHealth.unhealthy("Error getting health: " + e.getMessage()));
            }
        }
        return statuses;
    }

    /**
     * Check if an adapter is running.
     *
     * @param name adapter name
     * @return true if running
     */
    public boolean isAdapterRunning(String name) {
        ManagedAdapter managed = adapters.get(name);
        return managed != null && managed.isRunning();
    }

    /**
     * Internal class to track adapter state.
     */
    private static class ManagedAdapter {
        private final WeatherAdapter adapter;
        private final ProviderMetadata metadata;
        private volatile boolean running = false;

        ManagedAdapter(WeatherAdapter adapter, ProviderMetadata metadata) {
            this.adapter = adapter;
            this.metadata = metadata;
        }

        WeatherAdapter adapter() {
            return adapter;
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
