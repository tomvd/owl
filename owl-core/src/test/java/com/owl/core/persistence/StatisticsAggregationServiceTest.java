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
import com.owl.core.persistence.entity.*;
import com.owl.core.persistence.repository.EventRepository;
import com.owl.core.persistence.repository.StatisticsRepository;
import com.owl.core.persistence.repository.StatisticsShortTermRepository;
import com.owl.core.persistence.repository.WeatherEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StatisticsAggregationService.
 */
class StatisticsAggregationServiceTest {

    private TestMessageBus messageBus;
    private TestEventRepository eventRepository;
    private TestWeatherEntityRepository entityRepository;
    private TestStatisticsShortTermRepository shortTermRepository;
    private TestStatisticsRepository statisticsRepository;
    private StatisticsAggregationService service;

    @BeforeEach
    void setUp() {
        messageBus = new TestMessageBus();
        eventRepository = new TestEventRepository();
        entityRepository = new TestWeatherEntityRepository();
        shortTermRepository = new TestStatisticsShortTermRepository();
        statisticsRepository = new TestStatisticsRepository();

        service = new StatisticsAggregationService(
                messageBus,
                eventRepository,
                entityRepository,
                shortTermRepository,
                statisticsRepository
        );
    }

    @Test
    void archiveRecordTriggers5MinuteAggregation() {
        // Set up an entity
        WeatherEntity tempEntity = WeatherEntity.builder("sensor.davis_temp", "davis-serial")
                .friendlyName("Temperature")
                .aggregationMethod("mean")
                .build();
        entityRepository.save(tempEntity);

        // Set up events in the 5-minute window
        Instant windowEnd = Instant.parse("2025-01-15T12:05:00Z"); // 5-minute boundary
        Instant windowStart = windowEnd.minus(5, ChronoUnit.MINUTES);

        eventRepository.addEvent(Event.of(windowStart.plusSeconds(60), "sensor.davis_temp", 20.0));
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(120), "sensor.davis_temp", 21.0));
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(180), "sensor.davis_temp", 22.0));
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(240), "sensor.davis_temp", 21.0));

        // Initialize service (subscribes to message bus)
        service.initialize();

        // Publish a Davis archive record at the 5-minute boundary
        SensorReading archiveRecord = new SensorReading(
                windowEnd,
                "davis-serial",
                "sensor.davis_archive",
                1.0,
                null,
                true
        );
        messageBus.publishDirect(archiveRecord);

        // Verify statistics were created
        assertTrue(shortTermRepository.existsByStartTsAndEntityId(windowStart, "sensor.davis_temp"));

        StatisticsShortTerm stats = shortTermRepository.findLatestByEntityId("sensor.davis_temp").orElseThrow();
        assertEquals(windowStart, stats.startTs());
        assertEquals(21.0, stats.mean(), 0.01); // (20+21+22+21)/4 = 21
        assertEquals(20.0, stats.min());
        assertEquals(22.0, stats.max());
        assertEquals(21.0, stats.last()); // Last event value
        assertEquals(84.0, stats.sum(), 0.01); // 20+21+22+21 = 84
        assertEquals(4, stats.count());
    }

    @Test
    void nonArchiveRecordDoesNotTriggerAggregation() {
        // Set up an entity
        WeatherEntity tempEntity = WeatherEntity.builder("sensor.davis_temp", "davis-serial")
                .build();
        entityRepository.save(tempEntity);

        service.initialize();

        Instant timestamp = Instant.parse("2025-01-15T12:05:00Z");

        // Non-persistent reading (loop record) should not trigger
        SensorReading loopRecord = new SensorReading(
                timestamp,
                "davis-serial",
                "sensor.davis_temp",
                20.0,
                null,
                false // Not persistent
        );
        messageBus.publishDirect(loopRecord);

        // No statistics should be created
        assertFalse(shortTermRepository.existsByStartTsAndEntityId(
                timestamp.minus(5, ChronoUnit.MINUTES), "sensor.davis_temp"));
    }

    @Test
    void nonDavisSourceDoesNotTriggerAggregation() {
        service.initialize();

        Instant timestamp = Instant.parse("2025-01-15T12:05:00Z");

        // Reading from different source
        SensorReading reading = new SensorReading(
                timestamp,
                "openweather", // Not davis-serial
                "sensor.ow_temp",
                20.0,
                null,
                true
        );
        messageBus.publishDirect(reading);

        // No statistics should be created
        assertTrue(shortTermRepository.isEmpty());
    }

    @Test
    void timestampIsAlignedTo5MinuteBoundary() {
        // Set up an entity
        WeatherEntity tempEntity = WeatherEntity.builder("sensor.davis_temp", "davis-serial")
                .build();
        entityRepository.save(tempEntity);

        // Set up events in the 5-minute window [11:55, 12:00)
        Instant windowStart = Instant.parse("2025-01-15T11:55:00Z");
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(60), "sensor.davis_temp", 20.0));

        service.initialize();

        // Timestamp not exactly on 5-minute boundary, but should align to 12:00
        Instant timestamp = Instant.parse("2025-01-15T12:03:23Z");

        SensorReading reading = new SensorReading(
                timestamp,
                "davis-serial",
                "sensor.davis_temp",
                20.0,
                null,
                true
        );
        messageBus.publishDirect(reading);

        // Statistics should be created for the aligned window starting at 11:55
        // (aligned timestamp 12:00 minus 5 minutes = 11:55)
        assertTrue(shortTermRepository.existsByStartTsAndEntityId(windowStart, "sensor.davis_temp"));
    }

    @Test
    void gapFillingUsesLastKnownValue() {
        // Set up an entity
        WeatherEntity tempEntity = WeatherEntity.builder("sensor.davis_temp", "davis-serial")
                .build();
        entityRepository.save(tempEntity);

        // Add a previous statistics record (simulating cache population)
        Instant previousWindow = Instant.parse("2025-01-15T12:00:00Z");
        shortTermRepository.save(StatisticsShortTerm.of(
                previousWindow, "sensor.davis_temp", 19.5, 19.0, 20.0, 19.5, 78.0, 4
        ));

        service.initialize();

        // Trigger aggregation for a window with no events
        Instant windowEnd = Instant.parse("2025-01-15T12:10:00Z");
        // No events added for this window

        SensorReading archiveRecord = new SensorReading(
                windowEnd,
                "davis-serial",
                "sensor.davis_archive",
                1.0,
                null,
                true
        );
        messageBus.publishDirect(archiveRecord);

        // Verify gap-fill record was created
        Instant windowStart = windowEnd.minus(5, ChronoUnit.MINUTES);
        assertTrue(shortTermRepository.existsByStartTsAndEntityId(windowStart, "sensor.davis_temp"));

        StatisticsShortTerm stats = shortTermRepository.getByStartTsAndEntityId(windowStart, "sensor.davis_temp");
        assertEquals(19.5, stats.mean()); // Gap-filled with last value
        assertEquals(19.5, stats.min());
        assertEquals(19.5, stats.max());
        assertEquals(19.5, stats.last());
        assertEquals(0, stats.count()); // Count=0 indicates gap-fill
    }

    @Test
    void idempotencyPreventsDoubleAggregation() {
        // Set up an entity
        WeatherEntity tempEntity = WeatherEntity.builder("sensor.davis_temp", "davis-serial")
                .build();
        entityRepository.save(tempEntity);

        Instant windowEnd = Instant.parse("2025-01-15T12:05:00Z");
        Instant windowStart = windowEnd.minus(5, ChronoUnit.MINUTES);

        eventRepository.addEvent(Event.of(windowStart.plusSeconds(60), "sensor.davis_temp", 20.0));

        service.initialize();

        // First archive record
        SensorReading archiveRecord1 = new SensorReading(windowEnd, "davis-serial", "sensor.davis_archive", 1.0, null, true);
        messageBus.publishDirect(archiveRecord1);

        // Verify initial save
        assertEquals(1, shortTermRepository.countForEntity("sensor.davis_temp"));

        // Add more events (simulating new data)
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(120), "sensor.davis_temp", 25.0));

        // Second archive record with same timestamp (duplicate)
        SensorReading archiveRecord2 = new SensorReading(windowEnd, "davis-serial", "sensor.davis_archive", 1.0, null, true);
        messageBus.publishDirect(archiveRecord2);

        // Should still only have one record (idempotent)
        assertEquals(1, shortTermRepository.countForEntity("sensor.davis_temp"));

        // And it should have the original values, not the new ones
        StatisticsShortTerm stats = shortTermRepository.findLatestByEntityId("sensor.davis_temp").orElseThrow();
        assertEquals(20.0, stats.mean()); // Original value, not updated
    }

    @Test
    void hourlyRollupTriggeredAtHourBoundary() {
        // Set up an entity
        WeatherEntity tempEntity = WeatherEntity.builder("sensor.davis_temp", "davis-serial")
                .aggregationMethod("mean")
                .build();
        entityRepository.save(tempEntity);

        // Pre-populate 5-minute stats for the hour (12 records for one hour)
        Instant hourStart = Instant.parse("2025-01-15T12:00:00Z");
        for (int i = 0; i < 12; i++) {
            Instant windowStart = hourStart.plus(i * 5L, ChronoUnit.MINUTES);
            shortTermRepository.save(StatisticsShortTerm.of(
                    windowStart, "sensor.davis_temp",
                    20.0 + i, // mean: 20, 21, 22, ... 31
                    19.0 + i, // min
                    21.0 + i, // max
                    20.0 + i, // last
                    (20.0 + i) * 10, // sum
                    10 // count
            ));
        }

        service.initialize();

        // Trigger at hour boundary
        Instant hourEnd = Instant.parse("2025-01-15T13:00:00Z");
        SensorReading archiveRecord = new SensorReading(hourEnd, "davis-serial", "sensor.davis_archive", 1.0, null, true);
        messageBus.publishDirect(archiveRecord);

        // Verify hourly statistics were created
        assertTrue(statisticsRepository.existsByStartTsAndEntityId(hourStart, "sensor.davis_temp"));

        Statistics hourlyStats = statisticsRepository.findLatestByEntityId("sensor.davis_temp").orElseThrow();
        assertEquals(hourStart, hourlyStats.startTs());
        assertEquals(19.0, hourlyStats.min()); // Global min
        assertEquals(32.0, hourlyStats.max()); // Global max (21+11)
        assertEquals(31.0, hourlyStats.last()); // Last record's last value
        assertEquals(120, hourlyStats.count()); // 12 * 10
    }

    @Test
    void stateValueUsesEntityAggregationMethod() {
        // Test sum aggregation - fill all 12 5-minute slots in the hour
        WeatherEntity rainEntity = WeatherEntity.builder("sensor.davis_rain", "davis-serial")
                .aggregationMethod("sum")
                .build();
        entityRepository.save(rainEntity);

        Instant hourStart = Instant.parse("2025-01-15T12:00:00Z");
        double expectedSum = 0.0;

        // Fill all 12 5-minute slots (12:00, 12:05, ... 12:55)
        for (int i = 0; i < 12; i++) {
            Instant windowStart = hourStart.plus(i * 5L, ChronoUnit.MINUTES);
            double sum = 5.0 + i; // 5, 6, 7, ... 16
            expectedSum += sum;
            shortTermRepository.save(StatisticsShortTerm.of(
                    windowStart, "sensor.davis_rain", 1.0, 0.5, 1.5, 1.2, sum, 5
            ));
        }

        service.initialize();

        Instant hourEnd = Instant.parse("2025-01-15T13:00:00Z");
        SensorReading archiveRecord = new SensorReading(hourEnd, "davis-serial", "sensor.davis_archive", 1.0, null, true);
        messageBus.publishDirect(archiveRecord);

        Statistics hourlyStats = statisticsRepository.findLatestByEntityId("sensor.davis_rain").orElseThrow();
        // Sum of sums: 5+6+7+8+9+10+11+12+13+14+15+16 = 126
        assertEquals(expectedSum, hourlyStats.state());
    }

    @Test
    void multipleEntitiesAggregatedTogether() {
        // Set up multiple entities
        entityRepository.save(WeatherEntity.builder("sensor.davis_temp", "davis-serial").build());
        entityRepository.save(WeatherEntity.builder("sensor.davis_humidity", "davis-serial").build());
        entityRepository.save(WeatherEntity.builder("sensor.davis_pressure", "davis-serial").build());

        Instant windowEnd = Instant.parse("2025-01-15T12:05:00Z");
        Instant windowStart = windowEnd.minus(5, ChronoUnit.MINUTES);

        // Add events for all entities
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(60), "sensor.davis_temp", 20.0));
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(60), "sensor.davis_humidity", 65.0));
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(60), "sensor.davis_pressure", 1013.25));

        service.initialize();

        SensorReading archiveRecord = new SensorReading(windowEnd, "davis-serial", "sensor.davis_archive", 1.0, null, true);
        messageBus.publishDirect(archiveRecord);

        // All three entities should have statistics
        assertTrue(shortTermRepository.existsByStartTsAndEntityId(windowStart, "sensor.davis_temp"));
        assertTrue(shortTermRepository.existsByStartTsAndEntityId(windowStart, "sensor.davis_humidity"));
        assertTrue(shortTermRepository.existsByStartTsAndEntityId(windowStart, "sensor.davis_pressure"));
    }

    @Test
    void nonNumericEntityCapturesAttributesOnly() {
        // Set up a non-numeric entity (like weather icon)
        WeatherEntity iconEntity = WeatherEntity.builder("sensor.weather_icon", "davis-serial")
                .aggregationMethod("none")
                .build();
        entityRepository.save(iconEntity);

        Instant windowEnd = Instant.parse("2025-01-15T12:05:00Z");
        Instant windowStart = windowEnd.minus(5, ChronoUnit.MINUTES);

        // Add events with attributes (icon data)
        Map<String, Object> iconAttrs1 = Map.of("icon", "01d", "description", "clear sky");
        Map<String, Object> iconAttrs2 = Map.of("icon", "02d", "description", "few clouds");
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(60), "sensor.weather_icon", null, iconAttrs1));
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(180), "sensor.weather_icon", null, iconAttrs2));

        service.initialize();

        SensorReading archiveRecord = new SensorReading(windowEnd, "davis-serial", "sensor.davis_archive", 1.0, null, true);
        messageBus.publishDirect(archiveRecord);

        // Statistics should be created with attributes from the last event
        assertTrue(shortTermRepository.existsByStartTsAndEntityId(windowStart, "sensor.weather_icon"));

        StatisticsShortTerm stats = shortTermRepository.findLatestByEntityId("sensor.weather_icon").orElseThrow();
        assertNull(stats.mean()); // No numeric stats
        assertNull(stats.min());
        assertNull(stats.max());
        assertEquals(2, stats.count()); // Event count

        // Attributes should be from the last event
        assertNotNull(stats.attributes());
        assertEquals("02d", stats.attributes().get("icon"));
        assertEquals("few clouds", stats.attributes().get("description"));
    }

    @Test
    void eventsAtBoundaryTimestampAreIncluded() {
        // Davis archive events have timestamps exactly at the 5-minute boundary
        WeatherEntity tempEntity = WeatherEntity.builder("sensor.davis_temp", "davis-serial")
                .build();
        entityRepository.save(tempEntity);

        // Trigger at 12:05, which aggregates window (12:00, 12:05]
        Instant windowEnd = Instant.parse("2025-01-15T12:05:00Z");
        Instant windowStart = windowEnd.minus(5, ChronoUnit.MINUTES);

        // Davis archive event at exactly the boundary (12:05:00)
        eventRepository.addEvent(Event.of(windowEnd, "sensor.davis_temp", 20.0));

        service.initialize();

        SensorReading archiveRecord = new SensorReading(windowEnd, "davis-serial", "sensor.davis_archive", 1.0, null, true);
        messageBus.publishDirect(archiveRecord);

        // Event at boundary should be included
        assertTrue(shortTermRepository.existsByStartTsAndEntityId(windowStart, "sensor.davis_temp"));
        StatisticsShortTerm stats = shortTermRepository.findLatestByEntityId("sensor.davis_temp").orElseThrow();
        assertEquals(20.0, stats.mean());
        assertEquals(1, stats.count());
    }

    @Test
    void numericEntityAlsoCapturesAttributes() {
        // Set up a numeric entity that also has attributes
        WeatherEntity tempEntity = WeatherEntity.builder("sensor.davis_temp", "davis-serial")
                .aggregationMethod("mean")
                .build();
        entityRepository.save(tempEntity);

        Instant windowEnd = Instant.parse("2025-01-15T12:05:00Z");
        Instant windowStart = windowEnd.minus(5, ChronoUnit.MINUTES);

        // Add events with both state and attributes
        Map<String, Object> attrs = Map.of("quality", "good", "sensor_id", 42);
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(60), "sensor.davis_temp", 20.0, attrs));
        eventRepository.addEvent(Event.of(windowStart.plusSeconds(120), "sensor.davis_temp", 21.0, attrs));

        service.initialize();

        SensorReading archiveRecord = new SensorReading(windowEnd, "davis-serial", "sensor.davis_archive", 1.0, null, true);
        messageBus.publishDirect(archiveRecord);

        StatisticsShortTerm stats = shortTermRepository.findLatestByEntityId("sensor.davis_temp").orElseThrow();

        // Should have both numeric stats and attributes
        assertEquals(20.5, stats.mean(), 0.01);
        assertEquals(2, stats.count());
        assertNotNull(stats.attributes());
        assertEquals("good", stats.attributes().get("quality"));
    }

    // ========== Test Doubles ==========

    /**
     * Test message bus that allows direct event publishing for testing.
     */
    static class TestMessageBus extends MessageBusImpl {
        private final List<java.util.function.Consumer<WeatherEvent>> subscribers = new ArrayList<>();

        @Override
        public reactor.core.publisher.Flux<WeatherEvent> getEventFlux() {
            return reactor.core.publisher.Flux.create(sink -> {
                subscribers.add(sink::next);
            });
        }

        void publishDirect(WeatherEvent event) {
            for (var subscriber : subscribers) {
                subscriber.accept(event);
            }
        }
    }

    /**
     * In-memory event repository for testing.
     */
    static class TestEventRepository implements EventRepository {
        private final List<Event> events = new ArrayList<>();

        void addEvent(Event event) {
            events.add(event);
        }

        @Override
        public List<Event> findByEntityIdAndTimeRange(String entityId, Instant start, Instant end) {
            return events.stream()
                    .filter(e -> e.entityId().equals(entityId))
                    .filter(e -> !e.timestamp().isBefore(start) && e.timestamp().isBefore(end))
                    .sorted(Comparator.comparing(Event::timestamp))
                    .toList();
        }

        @Override
        public List<Event> findByEntityIdAndTimeRangeForStats(String entityId, Instant start, Instant end) {
            // Exclusive start, inclusive end (for events at boundary like Davis archives)
            return events.stream()
                    .filter(e -> e.entityId().equals(entityId))
                    .filter(e -> e.timestamp().isAfter(start) && !e.timestamp().isAfter(end))
                    .sorted(Comparator.comparing(Event::timestamp))
                    .toList();
        }

        @Override
        public Optional<Event> findLatestByEntityId(String entityId) {
            return events.stream()
                    .filter(e -> e.entityId().equals(entityId))
                    .max(Comparator.comparing(Event::timestamp));
        }

        @Override
        public Optional<Instant> findLatestTimestampBySource(String source) {
            return Optional.empty();
        }

        @Override
        public List<Event> findByEntityIdsAndTimeRange(List<String> entityIds, Instant start, Instant end) {
            return events.stream()
                    .filter(e -> entityIds.contains(e.entityId()))
                    .filter(e -> !e.timestamp().isBefore(start) && e.timestamp().isBefore(end))
                    .toList();
        }

        @Override
        public long countByEntityIdAndTimeRange(String entityId, Instant start, Instant end) {
            return findByEntityIdAndTimeRange(entityId, start, end).size();
        }

        @Override
        public void deleteByEntityIdAndTimestampBefore(String entityId, Instant before) {
            events.removeIf(e -> e.entityId().equals(entityId) && e.timestamp().isBefore(before));
        }

        @Override
        public <S extends Event> S save(S entity) {
            events.add(entity);
            return entity;
        }

        @Override
        public <S extends Event> S update(S entity) {
            return entity;
        }

        @Override
        public <S extends Event> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(e -> {
                events.add(e);
                result.add(e);
            });
            return result;
        }

        @Override
        public <S extends Event> List<S> updateAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(result::add);
            return result;
        }

        @Override
        public Optional<Event> findById(EventId id) {
            return events.stream()
                    .filter(e -> e.id().equals(id))
                    .findFirst();
        }

        @Override
        public boolean existsById(EventId id) {
            return findById(id).isPresent();
        }

        @Override
        public List<Event> findAll() {
            return new ArrayList<>(events);
        }

        @Override
        public long count() {
            return events.size();
        }

        @Override
        public void deleteById(EventId id) {
            events.removeIf(e -> e.id().equals(id));
        }

        @Override
        public void delete(Event entity) {
            events.remove(entity);
        }

        @Override
        public void deleteAll(Iterable<? extends Event> entities) {
            entities.forEach(events::remove);
        }

        @Override
        public void deleteAll() {
            events.clear();
        }
    }

    /**
     * In-memory weather entity repository for testing.
     */
    static class TestWeatherEntityRepository implements WeatherEntityRepository {
        private final Map<String, WeatherEntity> entities = new ConcurrentHashMap<>();

        @Override
        public List<WeatherEntity> findBySource(String source) {
            return entities.values().stream()
                    .filter(e -> source.equals(e.getSource()))
                    .toList();
        }

        @Override
        public List<WeatherEntity> findByAggregationMethod(String aggregationMethod) {
            return entities.values().stream()
                    .filter(e -> aggregationMethod.equals(e.getAggregationMethod()))
                    .toList();
        }

        @Override
        public List<WeatherEntity> findByDeviceClass(String deviceClass) {
            return entities.values().stream()
                    .filter(e -> deviceClass.equals(e.getDeviceClass()))
                    .toList();
        }

        @Override
        public boolean existsByEntityId(String entityId) {
            return entities.containsKey(entityId);
        }

        @Override
        public <S extends WeatherEntity> S save(S entity) {
            entities.put(entity.getEntityId(), entity);
            return entity;
        }

        @Override
        public <S extends WeatherEntity> S update(S entity) {
            entities.put(entity.getEntityId(), entity);
            return entity;
        }

        @Override
        public <S extends WeatherEntity> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(e -> {
                this.entities.put(e.getEntityId(), e);
                result.add(e);
            });
            return result;
        }

        @Override
        public <S extends WeatherEntity> List<S> updateAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(e -> {
                this.entities.put(e.getEntityId(), e);
                result.add(e);
            });
            return result;
        }

        @Override
        public Optional<WeatherEntity> findById(String id) {
            return Optional.ofNullable(entities.get(id));
        }

        @Override
        public boolean existsById(String id) {
            return entities.containsKey(id);
        }

        @Override
        public List<WeatherEntity> findAll() {
            return new ArrayList<>(entities.values());
        }

        @Override
        public long count() {
            return entities.size();
        }

        @Override
        public void deleteById(String id) {
            entities.remove(id);
        }

        @Override
        public void delete(WeatherEntity entity) {
            entities.remove(entity.getEntityId());
        }

        @Override
        public void deleteAll(Iterable<? extends WeatherEntity> entities) {
            entities.forEach(this::delete);
        }

        @Override
        public void deleteAll() {
            entities.clear();
        }
    }

    /**
     * In-memory short-term statistics repository for testing.
     */
    static class TestStatisticsShortTermRepository implements StatisticsShortTermRepository {
        private final List<StatisticsShortTerm> stats = new ArrayList<>();

        boolean isEmpty() {
            return stats.isEmpty();
        }

        int countForEntity(String entityId) {
            return (int) stats.stream().filter(s -> s.entityId().equals(entityId)).count();
        }

        StatisticsShortTerm getByStartTsAndEntityId(Instant startTs, String entityId) {
            return stats.stream()
                    .filter(s -> s.startTs().equals(startTs) && s.entityId().equals(entityId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<StatisticsShortTerm> findByTimeRange(Instant start, Instant end) {
            return stats.stream()
                    .filter(s -> !s.startTs().isBefore(start) && s.startTs().isBefore(end))
                    .sorted(Comparator.comparing(StatisticsShortTerm::startTs))
                    .toList();
        }

        @Override
        public List<StatisticsShortTerm> findByEntityIdAndTimeRange(String entityId, Instant start, Instant end) {
            return stats.stream()
                    .filter(s -> s.entityId().equals(entityId))
                    .filter(s -> !s.startTs().isBefore(start) && s.startTs().isBefore(end))
                    .sorted(Comparator.comparing(StatisticsShortTerm::startTs))
                    .toList();
        }

        @Override
        public Optional<StatisticsShortTerm> findLatestByEntityId(String entityId) {
            return stats.stream()
                    .filter(s -> s.entityId().equals(entityId))
                    .max(Comparator.comparing(StatisticsShortTerm::startTs));
        }

        @Override
        public boolean existsByStartTsAndEntityId(Instant startTs, String entityId) {
            return stats.stream()
                    .anyMatch(s -> s.startTs().equals(startTs) && s.entityId().equals(entityId));
        }

        @Override
        public <S extends StatisticsShortTerm> S save(S entity) {
            stats.add(entity);
            return entity;
        }

        @Override
        public <S extends StatisticsShortTerm> S update(S entity) {
            stats.removeIf(s -> s.id().equals(entity.id()));
            stats.add(entity);
            return entity;
        }

        @Override
        public <S extends StatisticsShortTerm> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(e -> {
                stats.add(e);
                result.add(e);
            });
            return result;
        }

        @Override
        public <S extends StatisticsShortTerm> List<S> updateAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(e -> {
                update(e);
                result.add(e);
            });
            return result;
        }

        @Override
        public Optional<StatisticsShortTerm> findById(StatisticsId id) {
            return stats.stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override
        public boolean existsById(StatisticsId id) {
            return findById(id).isPresent();
        }

        @Override
        public List<StatisticsShortTerm> findAll() {
            return new ArrayList<>(stats);
        }

        @Override
        public long count() {
            return stats.size();
        }

        @Override
        public void deleteById(StatisticsId id) {
            stats.removeIf(s -> s.id().equals(id));
        }

        @Override
        public void delete(StatisticsShortTerm entity) {
            stats.remove(entity);
        }

        @Override
        public void deleteAll(Iterable<? extends StatisticsShortTerm> entities) {
            entities.forEach(stats::remove);
        }

        @Override
        public void deleteAll() {
            stats.clear();
        }
    }

    /**
     * In-memory statistics repository for testing.
     */
    static class TestStatisticsRepository implements StatisticsRepository {
        private final List<Statistics> stats = new ArrayList<>();

        @Override
        public Optional<Statistics> findLatestByEntityId(String entityId) {
            return stats.stream()
                    .filter(s -> s.entityId().equals(entityId))
                    .max(Comparator.comparing(Statistics::startTs));
        }

        @Override
        public boolean existsByStartTsAndEntityId(Instant startTs, String entityId) {
            return stats.stream()
                    .anyMatch(s -> s.startTs().equals(startTs) && s.entityId().equals(entityId));
        }

        @Override
        public <S extends Statistics> S save(S entity) {
            stats.add(entity);
            return entity;
        }

        @Override
        public <S extends Statistics> S update(S entity) {
            stats.removeIf(s -> s.id().equals(entity.id()));
            stats.add(entity);
            return entity;
        }

        @Override
        public <S extends Statistics> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(e -> {
                stats.add(e);
                result.add(e);
            });
            return result;
        }

        @Override
        public <S extends Statistics> List<S> updateAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(e -> {
                update(e);
                result.add(e);
            });
            return result;
        }

        @Override
        public Optional<Statistics> findById(StatisticsId id) {
            return stats.stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override
        public boolean existsById(StatisticsId id) {
            return findById(id).isPresent();
        }

        @Override
        public List<Statistics> findAll() {
            return new ArrayList<>(stats);
        }

        @Override
        public long count() {
            return stats.size();
        }

        @Override
        public void deleteById(StatisticsId id) {
            stats.removeIf(s -> s.id().equals(id));
        }

        @Override
        public void delete(Statistics entity) {
            stats.remove(entity);
        }

        @Override
        public void deleteAll(Iterable<? extends Statistics> entities) {
            entities.forEach(stats::remove);
        }

        @Override
        public void deleteAll() {
            stats.clear();
        }
    }
}
