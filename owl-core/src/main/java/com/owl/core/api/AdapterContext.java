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

import org.slf4j.Logger;

/**
 * Context provided to adapters during initialization.
 * Gives access to framework services.
 */
public interface AdapterContext {

    /**
     * Get the message bus for publishing events.
     *
     * @return message bus instance
     */
    MessageBus getMessageBus();

    /**
     * Get configuration specific to this adapter.
     * Configuration is loaded from application.yml under:
     * <pre>
     * owl:
     *   adapters:
     *     {adapter-name}:
     *       ...
     * </pre>
     *
     * @return adapter configuration
     */
    AdapterConfiguration getConfiguration();

    /**
     * Get a logger for this adapter.
     *
     * @return logger instance
     */
    Logger getLogger();

    /**
     * Get the entity registry for looking up entity metadata.
     *
     * @return entity registry
     */
    EntityRegistry getEntityRegistry();

    /**
     * Get metrics registry for reporting adapter-specific metrics.
     *
     * @return metrics registry
     */
    MetricsRegistry getMetrics();
}
