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

import java.time.*;
import java.util.List;

/**
 * Generates current.json on each statistics computation.
 */
@Singleton
@Requires(property = "owl.services.export.enabled", value = "true")
public class CurrentExporter {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentExporter.class);

    private final MessageBus messageBus;
    private final StatisticsAccess statisticsAccess;
    private final EntityRegistry entityRegistry;
    private final List<ExportDestination> destinations;
    private final JsonExporter jsonExporter;

    public CurrentExporter(
            MessageBus messageBus,
            StatisticsAccess statisticsAccess,
            EntityRegistry entityRegistry,
            List<ExportDestination> destinations,
            JsonExporter jsonExporter) {
        this.messageBus = messageBus;
        this.statisticsAccess = statisticsAccess;
        this.entityRegistry = entityRegistry;
        this.destinations = destinations;
        this.jsonExporter = jsonExporter;
    }

    @PostConstruct
    void init() {
        messageBus.subscribe(StatisticsComputedEvent.class, this::onStatisticsComputed);
        LOG.info("CurrentExporter initialized");
    }

    private void onStatisticsComputed(StatisticsComputedEvent event) {
        Instant windowEnd = event.windowEnd();

        try {
            Instant windowStart = windowEnd.minusSeconds(300);
            List<ShortTermRecord> latestRecords = statisticsAccess.getShortTermStatistics(windowStart, windowEnd);

            LocalDate today = LocalDate.ofInstant(windowEnd, ZoneOffset.UTC);
            Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
            List<ShortTermRecord> todayRecords = statisticsAccess.getShortTermStatistics(dayStart, windowEnd);

            byte[] json = jsonExporter.generateCurrent(windowEnd, latestRecords, todayRecords, entityRegistry);
            writeToDestinations("current.json", json);
            LOG.debug("Generated current.json ({} bytes)", json.length);
        } catch (Exception e) {
            LOG.error("Failed to generate current.json", e);
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
