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
import com.owl.core.persistence.entity.Statistics;
import com.owl.core.persistence.entity.StatisticsShortTerm;
import com.owl.core.persistence.entity.WeatherEntity;
import com.owl.core.persistence.repository.EventRepository;
import com.owl.core.persistence.repository.StatisticsRepository;
import com.owl.core.persistence.repository.StatisticsShortTermRepository;
import com.owl.core.persistence.repository.WeatherEntityRepository;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service that aggregates raw events into statistics.
 * <p>
 * Listens for Davis archive records (5-minute boundary) as triggers to compute
 * 5-minute statistics for all entities. On hour boundaries, also rolls up to
 * hourly statistics.
 */
@Singleton
@Context
public class StatisticsAggregationService {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsAggregationService.class);
    private static final String DAVIS_SOURCE = "davis-serial";
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final MessageBusImpl messageBus;
    private final EventRepository eventRepository;
    private final WeatherEntityRepository entityRepository;
    private final StatisticsShortTermRepository shortTermRepository;
    private final StatisticsRepository statisticsRepository;

    private Disposable subscription;

    // Track last processed timestamp to avoid duplicate triggers from batch archive records
    private volatile Instant lastProcessedTimestamp;

    // Cache of last known values per entity for gap filling
    private final Map<String, Double> lastKnownValues = new ConcurrentHashMap<>();

    public StatisticsAggregationService(
            MessageBusImpl messageBus,
            EventRepository eventRepository,
            WeatherEntityRepository entityRepository,
            StatisticsShortTermRepository shortTermRepository,
            StatisticsRepository statisticsRepository) {
        this.messageBus = messageBus;
        this.eventRepository = eventRepository;
        this.entityRepository = entityRepository;
        this.shortTermRepository = shortTermRepository;
        this.statisticsRepository = statisticsRepository;
    }

    @PostConstruct
    void initialize() {
        LOG.info("Starting statistics aggregation service");

        subscription = messageBus.getEventFlux()
                .filter(this::isArchiveRecord)
                .map(event -> (SensorReading) event)
                .subscribe(
                        this::onArchiveRecord,
                        error -> LOG.error("waitError in statistics aggregation stream", error)
                );

        LOG.info("Statistics aggregation service started");
    }

    @PreDestroy
    void shutdown() {
        LOG.info("Shutting down statistics aggregation service");
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    /**
     * Check if the event is a Davis archive record (trigger for aggregation).
     */
    private boolean isArchiveRecord(WeatherEvent event) {
        if (!(event instanceof SensorReading reading)) {
            return false;
        }

        // Must be from Davis and persistent (archive records are persistent, loop records are not)
        return DAVIS_SOURCE.equals(reading.getSource()) && reading.isPersistent();
    }

    /**
     * Align a timestamp to the previous 5-minute boundary.
     * E.g., 12:07:23 -> 12:05:00, 12:05:01 -> 12:05:00
     */
    private Instant alignTo5MinuteBoundary(Instant timestamp) {
        long epochSeconds = timestamp.getEpochSecond();
        long alignedSeconds = (epochSeconds / 300) * 300;
        return Instant.ofEpochSecond(alignedSeconds);
    }

    /**
     * Handle an archive record - trigger 5-minute aggregation.
     */
    private void onArchiveRecord(SensorReading reading) {
        // Align timestamp to 5-minute boundary (archive records may be slightly off)
        Instant alignedTimestamp = alignTo5MinuteBoundary(reading.getTimestamp());

        // Deduplicate: skip if we already processed this aligned timestamp
        if (alignedTimestamp.equals(lastProcessedTimestamp)) {
            LOG.debug("Skipping duplicate trigger for aligned timestamp {}", alignedTimestamp);
            return;
        }

        LOG.info("Archive record received at {}, triggering aggregation for window ending at {}",
                reading.getTimestamp(), alignedTimestamp);
        lastProcessedTimestamp = alignedTimestamp;

        try {
            aggregate5Minutes(alignedTimestamp);

            // If this is an hour boundary, also do hourly rollup
            if (isHourBoundary(alignedTimestamp)) {
                Instant hourStart = alignedTimestamp.minus(ONE_HOUR);
                aggregateHourly(hourStart, alignedTimestamp);
            }
        } catch (Exception e) {
            LOG.error("Failed to aggregate statistics for timestamp {}", alignedTimestamp, e);
        }
    }

    /**
     * Check if timestamp is on an hour boundary (minute == 0).
     */
    private boolean isHourBoundary(Instant timestamp) {
        return timestamp.getEpochSecond() % 3600 == 0;
    }

    /**
     * Aggregate events from the previous 5 minutes into statistics_short_term.
     */
    private void aggregate5Minutes(Instant windowEnd) {
        Instant windowStart = windowEnd.minus(FIVE_MINUTES);

        List<WeatherEntity> entities = (List<WeatherEntity>) entityRepository.findAll();
        LOG.info("Aggregating 5-minute statistics for window [{}, {}), found {} entities",
                windowStart, windowEnd, entities.size());

        for (WeatherEntity entity : entities) {
            try {
                aggregate5MinutesForEntity(entity, windowStart, windowEnd);
            } catch (Exception e) {
                LOG.error("Failed to aggregate entity {} for window [{}, {})",
                        entity.getEntityId(), windowStart, windowEnd, e);
            }
        }

        LOG.debug("Completed 5-minute aggregation for {} entities", entities.size());
    }

    /**
     * Aggregate 5-minute statistics for a single entity.
     */
    private void aggregate5MinutesForEntity(WeatherEntity entity, Instant windowStart, Instant windowEnd) {
        String entityId = entity.getEntityId();
        String aggregationMethod = entity.getAggregationMethod();
        boolean isNonNumeric = "none".equalsIgnoreCase(aggregationMethod);

        // Idempotency check: skip if already aggregated
        if (shortTermRepository.existsByStartTsAndEntityId(windowStart, entityId)) {
            LOG.trace("Statistics already exist for {} at {}, skipping", entityId, windowStart);
            return;
        }

        // Query events in the window (exclusive start, inclusive end)
        // This ensures events at boundary timestamps (like Davis archives) are included
        var events = eventRepository.findByEntityIdAndTimeRangeForStats(entityId, windowStart, windowEnd);

        StatisticsShortTerm stats;
        if (events.isEmpty()) {
            if (isNonNumeric) {
                // Non-numeric entities don't need gap-fill
                LOG.trace("No events for non-numeric entity {}, skipping", entityId);
                return;
            }
            // Gap fill: use last known value
            Double lastValue = getLastKnownValue(entityId);
            if (lastValue == null) {
                LOG.trace("No events and no last known value for {}, skipping", entityId);
                return;
            }
            stats = StatisticsShortTerm.gapFill(windowStart, entityId, lastValue);
            LOG.trace("Gap-filled {} with value {}", entityId, lastValue);
        } else if (isNonNumeric) {
            // Non-numeric entity: only capture attributes from last event
            var lastEvent = events.get(events.size() - 1);
            stats = StatisticsShortTerm.ofAttributes(windowStart, entityId, lastEvent.attributes(), events.size());
            LOG.trace("Captured attributes for non-numeric entity {}", entityId);
        } else {
            // Compute statistics from events
            DoubleSummaryStatistics summary = events.stream()
                    .filter(e -> e.state() != null)
                    .mapToDouble(e -> e.state())
                    .summaryStatistics();

            if (summary.getCount() == 0) {
                LOG.trace("No valid state values for {} in window, skipping", entityId);
                return;
            }

            double mean = summary.getAverage();
            double min = summary.getMin();
            double max = summary.getMax();
            var lastEvent = events.get(events.size() - 1);
            double last = lastEvent.state();
            double sum = summary.getSum();
            int count = (int) summary.getCount();

            // Capture attributes from last event
            var attributes = lastEvent.attributes();

            stats = StatisticsShortTerm.of(windowStart, entityId, mean, min, max, last, sum, count, attributes);

            // Update last known value cache
            lastKnownValues.put(entityId, last);
        }

        shortTermRepository.save(stats);
        LOG.info("Saved short-term statistics for {} at {}: mean={}, count={}",
                entityId, windowStart, stats.mean(), stats.count());
    }

    /**
     * Get the last known value for an entity, checking cache first then database.
     */
    private Double getLastKnownValue(String entityId) {
        // Check cache first
        Double cached = lastKnownValues.get(entityId);
        if (cached != null) {
            return cached;
        }

        // Fall back to database
        return shortTermRepository.findLatestByEntityId(entityId)
                .map(StatisticsShortTerm::last)
                .orElse(null);
    }

    /**
     * Roll up 5-minute statistics to hourly statistics.
     */
    private void aggregateHourly(Instant hourStart, Instant hourEnd) {
        LOG.debug("Rolling up hourly statistics for window [{}, {})", hourStart, hourEnd);

        // Get all short-term statistics for the hour
        List<StatisticsShortTerm> shortTermStats = shortTermRepository.findByTimeRange(hourStart, hourEnd);

        // Group by entity
        Map<String, List<StatisticsShortTerm>> byEntity = shortTermStats.stream()
                .collect(Collectors.groupingBy(StatisticsShortTerm::entityId));

        for (Map.Entry<String, List<StatisticsShortTerm>> entry : byEntity.entrySet()) {
            try {
                aggregateHourlyForEntity(entry.getKey(), hourStart, entry.getValue());
            } catch (Exception e) {
                LOG.error("Failed to aggregate hourly stats for entity {} at {}",
                        entry.getKey(), hourStart, e);
            }
        }

        LOG.debug("Completed hourly rollup for {} entities", byEntity.size());
    }

    /**
     * Compute hourly statistics for a single entity from its 5-minute records.
     */
    private void aggregateHourlyForEntity(String entityId, Instant hourStart, List<StatisticsShortTerm> records) {
        // Idempotency check
        if (statisticsRepository.existsByStartTsAndEntityId(hourStart, entityId)) {
            LOG.trace("Hourly statistics already exist for {} at {}, skipping", entityId, hourStart);
            return;
        }

        if (records.isEmpty()) {
            return;
        }

        // Get entity to check aggregation method
        WeatherEntity entity = entityRepository.findById(entityId).orElse(null);
        boolean isNonNumeric = entity != null && "none".equalsIgnoreCase(entity.getAggregationMethod());

        // Get last record for attributes and last value
        StatisticsShortTerm lastRecord = records.get(records.size() - 1);
        var attributes = lastRecord.attributes();

        Statistics hourlyStats;
        if (isNonNumeric) {
            // Non-numeric entity: only store attributes
            int totalCount = records.stream()
                    .mapToInt(r -> r.count() != null ? r.count() : 0)
                    .sum();
            hourlyStats = Statistics.ofAttributes(hourStart, entityId, attributes, totalCount);
        } else {
            // Compute weighted mean (weight by count, excluding gap-fills with count=0)
            double totalWeightedSum = 0;
            int totalCount = 0;
            double globalMin = Double.MAX_VALUE;
            double globalMax = Double.MIN_VALUE;
            double totalSum = 0;

            for (StatisticsShortTerm record : records) {
                if (record.count() != null && record.count() > 0 && record.mean() != null) {
                    totalWeightedSum += record.mean() * record.count();
                    totalCount += record.count();
                }
                if (record.min() != null && record.min() < globalMin) {
                    globalMin = record.min();
                }
                if (record.max() != null && record.max() > globalMax) {
                    globalMax = record.max();
                }
                if (record.sum() != null) {
                    totalSum += record.sum();
                }
            }

            Double last = lastRecord.last();

            // Compute mean (fall back to simple average if no counts)
            Double mean;
            if (totalCount > 0) {
                mean = totalWeightedSum / totalCount;
            } else {
                // All records were gap-fills, use simple average of means
                mean = records.stream()
                        .filter(r -> r.mean() != null)
                        .mapToDouble(StatisticsShortTerm::mean)
                        .average()
                        .orElse(0);
            }

            // Determine state value based on entity's aggregation method
            Double state = determineStateValue(entity, mean, globalMin, globalMax, last, totalSum, totalCount);

            hourlyStats = Statistics.of(
                    hourStart,
                    entityId,
                    mean,
                    globalMin == Double.MAX_VALUE ? null : globalMin,
                    globalMax == Double.MIN_VALUE ? null : globalMax,
                    last,
                    totalSum,
                    totalCount,
                    state,
                    attributes
            );
        }

        statisticsRepository.save(hourlyStats);
        LOG.trace("Saved hourly statistics for {} at {}", entityId, hourStart);
    }

    /**
     * Determine the state value based on entity's aggregation method.
     */
    private Double determineStateValue(WeatherEntity entity, Double mean, Double min, Double max,
                                       Double last, Double sum, Integer count) {
        if (entity == null) {
            return mean; // Default to mean
        }

        String method = entity.getAggregationMethod();
        if (method == null) {
            method = "mean";
        }

        return switch (method.toLowerCase()) {
            case "sum" -> sum;
            case "min" -> min == Double.MAX_VALUE ? null : min;
            case "max" -> max == Double.MIN_VALUE ? null : max;
            case "last" -> last;
            case "count" -> count != null ? count.doubleValue() : null;
            default -> mean; // "mean" or unknown
        };
    }
}
