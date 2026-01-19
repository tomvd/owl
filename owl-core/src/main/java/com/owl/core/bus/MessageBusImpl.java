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
package com.owl.core.bus;

import com.owl.core.api.MessageBus;
import com.owl.core.api.MessageBusException;
import com.owl.core.api.WeatherEvent;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Consumer;

/**
 * Internal message bus implementation using Project Reactor.
 * <p>
 * Provides a multi-producer, multi-consumer event bus with backpressure support.
 */
@Singleton
@Context
public class MessageBusImpl implements MessageBus {

    private static final Logger LOG = LoggerFactory.getLogger(MessageBusImpl.class);
    private static final int BUFFER_SIZE = 10_000;

    private Sinks.Many<WeatherEvent> sink;
    private Flux<WeatherEvent> flux;

    @PostConstruct
    void initialize() {
        LOG.info("Initializing message bus with buffer size: {}", BUFFER_SIZE);

        this.sink = Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE);

        this.flux = sink.asFlux()
                .publishOn(Schedulers.boundedElastic())
                .share();

        LOG.info("Message bus initialized");
    }

    @PreDestroy
    void shutdown() {
        LOG.info("Shutting down message bus");
        sink.tryEmitComplete();
    }

    @Override
    public void publish(WeatherEvent event) throws MessageBusException {
        if (event == null) {
            throw new MessageBusException("Event cannot be null");
        }

        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            LOG.warn("Failed to publish event: {}, result: {}", event.getEventType(), result);
            throw new MessageBusException("Failed to publish event: " + result);
        }

        LOG.trace("Published event: {} from {}", event.getEventType(), event.getSource());
    }

    @Override
    public void publishBatch(List<? extends WeatherEvent> events) throws MessageBusException {
        if (events == null || events.isEmpty()) {
            return;
        }

        for (WeatherEvent event : events) {
            publish(event);
        }
    }

    /**
     * Get a flux of all events.
     * <p>
     * This is used internally by the persistence layer and other consumers.
     *
     * @return flux of events
     */
    public Flux<WeatherEvent> getEventFlux() {
        return flux;
    }

    /**
     * Subscribe to events of a specific type.
     *
     * @param eventType event type class
     * @param consumer  event consumer
     * @param <T>       event type
     */
    public <T extends WeatherEvent> void subscribe(Class<T> eventType, Consumer<T> consumer) {
        flux.filter(eventType::isInstance)
                .map(eventType::cast)
                .subscribe(consumer);
    }

    /**
     * Subscribe to all events.
     *
     * @param consumer event consumer
     */
    public void subscribeAll(Consumer<WeatherEvent> consumer) {
        flux.subscribe(consumer);
    }
}
