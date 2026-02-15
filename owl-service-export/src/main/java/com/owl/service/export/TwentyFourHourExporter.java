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
package com.owl.service.export;

import com.owl.core.api.*;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Generates 24h.json on each statistics computation.
 */
@Singleton
@Requires(property = "owl.services.export.enabled", value = "true")
public class TwentyFourHourExporter {

    private static final Logger LOG = LoggerFactory.getLogger(TwentyFourHourExporter.class);

    private final MessageBus messageBus;
    private final StatisticsAccess statisticsAccess;
    private final EntityRegistry entityRegistry;
    private final List<ExportDestination> destinations;
    private final JsonExporter jsonExporter;
    private final ExportConfiguration config;

    public TwentyFourHourExporter(
            MessageBus messageBus,
            StatisticsAccess statisticsAccess,
            EntityRegistry entityRegistry,
            List<ExportDestination> destinations,
            JsonExporter jsonExporter,
            ExportConfiguration config) {
        this.messageBus = messageBus;
        this.statisticsAccess = statisticsAccess;
        this.entityRegistry = entityRegistry;
        this.destinations = destinations;
        this.jsonExporter = jsonExporter;
        this.config = config;
    }

    @PostConstruct
    void init() {
        messageBus.subscribe(StatisticsComputedEvent.class, this::onStatisticsComputed);
        LOG.info("TwentyFourHourExporter initialized, entities: {}", config.getEntities24hSet());
    }

    private void onStatisticsComputed(StatisticsComputedEvent event) {
        Set<String> entityIds24h = config.getEntities24hSet();
        if (entityIds24h.isEmpty()) {
            LOG.debug("No entities configured for 24h export, skipping");
            return;
        }

        Instant windowEnd = event.windowEnd();

        try {
            Instant start24h = windowEnd.minus(Duration.ofHours(24));
            List<ShortTermRecord> records = statisticsAccess.getShortTermStatistics(start24h, windowEnd);

            byte[] json = jsonExporter.generate24h(windowEnd, records, entityIds24h, entityRegistry);
            writeToDestinations("24h.json", json);
            LOG.debug("Generated 24h.json ({} bytes)", json.length);
        } catch (Exception e) {
            LOG.error("Failed to generate 24h.json", e);
        }
    }

    private void writeToDestinations(String path, byte[] data) {
        for (ExportDestination destination : destinations) {
            try {
                destination.write(path, data);
            } catch (Exception e) {
                LOG.error("Failed to write {} to destination {}", path, destination.getClass().getSimpleName(), e);
            }
        }
    }
}
