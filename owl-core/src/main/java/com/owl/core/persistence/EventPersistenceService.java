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
package com.owl.core.persistence;

import com.owl.core.api.SensorReading;
import com.owl.core.api.WeatherEvent;
import com.owl.core.bus.MessageBusImpl;
import com.owl.core.persistence.entity.Event;
import com.owl.core.persistence.repository.EventRepository;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * Service that subscribes to the message bus and persists events to the database.
 */
@Singleton
@Context
public class EventPersistenceService {

    private static final Logger LOG = LoggerFactory.getLogger(EventPersistenceService.class);

    private final MessageBusImpl messageBus;
    private final EventRepository eventRepository;
    private Disposable subscription;

    public EventPersistenceService(MessageBusImpl messageBus, EventRepository eventRepository) {
        this.messageBus = messageBus;
        this.eventRepository = eventRepository;
    }

    @PostConstruct
    void initialize() {
        LOG.info("Starting event persistence service");

        subscription = messageBus.getEventFlux()
                .filter(event -> event instanceof SensorReading)
                .map(event -> (SensorReading) event)
                .filter(SensorReading::isPersistent)
                .subscribe(
                        this::persistSensorReading,
                        error -> LOG.error("Error in persistence stream", error)
                );

        LOG.info("Event persistence service started");
    }

    /**
     * Persist a sensor reading to the database.
     */
    private void persistSensorReading(SensorReading reading) {
        try {
            Event event = Event.of(
                    reading.getTimestamp(),
                    reading.getEntityId(),
                    reading.getValue(),
                    reading.getAttributes()
            );

            eventRepository.save(event);
            LOG.trace("Persisted event: {} = {}", reading.getEntityId(), reading.getValue());

        } catch (Exception e) {
            LOG.error("Failed to persist event: {}", reading.getEntityId(), e);
        }
    }
}
