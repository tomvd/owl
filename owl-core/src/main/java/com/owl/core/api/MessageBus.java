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

import java.util.List;
import java.util.function.Consumer;

/**
 * Message bus for publishing and subscribing to weather events.
 * <p>
 * Thread-safe. All publish operations are non-blocking.
 */
public interface MessageBus {

    /**
     * Publish a weather event to the bus.
     * This method should not block for long periods.
     *
     * @param event event to publish
     * @throws MessageBusException if event cannot be published
     */
    void publish(WeatherEvent event) throws MessageBusException;

    /**
     * Publish multiple events in a batch.
     * More efficient than multiple single publishes.
     *
     * @param events events to publish
     * @throws MessageBusException if events cannot be published
     */
    void publishBatch(List<? extends WeatherEvent> events) throws MessageBusException;

    /**
     * Subscribe to events of a specific type.
     *
     * @param eventType event type class to filter on
     * @param consumer  event consumer
     * @param <T>       event type
     */
    <T extends WeatherEvent> void subscribe(Class<T> eventType, Consumer<T> consumer);
}
