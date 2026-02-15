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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.owl.core.api.EntityDefinition;
import com.owl.core.api.EntityRegistry;
import com.owl.core.api.ShortTermRecord;
import jakarta.inject.Singleton;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates the three export JSON files from statistics records.
 */
@Singleton
public class JsonExporter {

    private final ObjectMapper objectMapper;

    public JsonExporter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Generate current.json: latest statistics record + today's min/max/sum aggregates.
     */
    public byte[] generateCurrent(
            Instant windowEnd,
            List<ShortTermRecord> latestRecords,
            List<ShortTermRecord> todayRecords,
            EntityRegistry entityRegistry) throws Exception {

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("timestamp", windowEnd.toString());

        Map<String, Object> current = new LinkedHashMap<>();
        for (ShortTermRecord record : latestRecords) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("mean", record.mean());
            entry.put("min", record.min());
            entry.put("max", record.max());
            entry.put("last", record.last());
            String unit = getUnit(record.entityId(), entityRegistry);
            if (unit != null) {
                entry.put("unit", unit);
            }
            current.put(record.entityId(), entry);
        }
        root.put("current", current);

        Map<String, List<ShortTermRecord>> byEntity = todayRecords.stream()
                .collect(Collectors.groupingBy(ShortTermRecord::entityId, LinkedHashMap::new, Collectors.toList()));

        Map<String, Object> today = new LinkedHashMap<>();
        for (Map.Entry<String, List<ShortTermRecord>> entry : byEntity.entrySet()) {
            List<ShortTermRecord> records = entry.getValue();
            today.put(entry.getKey(), computeDailySummary(records));
        }
        root.put("today", today);

        return objectMapper.writeValueAsBytes(root);
    }

    /**
     * Generate 24h.json: last 24 hours of 5-minute values for selected entities.
     */
    public byte[] generate24h(
            Instant windowEnd,
            List<ShortTermRecord> records,
            Set<String> entityIds24h,
            EntityRegistry entityRegistry) throws Exception {

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generated", windowEnd.toString());
        root.put("interval_minutes", 5);

        Map<String, List<ShortTermRecord>> byEntity = records.stream()
                .filter(r -> entityIds24h.contains(r.entityId()))
                .collect(Collectors.groupingBy(ShortTermRecord::entityId, LinkedHashMap::new, Collectors.toList()));

        Map<String, Object> entities = new LinkedHashMap<>();
        for (Map.Entry<String, List<ShortTermRecord>> entry : byEntity.entrySet()) {
            Map<String, Object> entityData = new LinkedHashMap<>();
            String unit = getUnit(entry.getKey(), entityRegistry);
            if (unit != null) {
                entityData.put("unit", unit);
            }

            List<Map<String, Object>> values = new ArrayList<>();
            for (ShortTermRecord record : entry.getValue()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("timestamp", record.startTs().toString());
                value.put("mean", record.mean());
                values.add(value);
            }
            entityData.put("values", values);
            entities.put(entry.getKey(), entityData);
        }
        root.put("entities", entities);

        return objectMapper.writeValueAsBytes(root);
    }

    /**
     * Generate archive/{yyyy}/{MM}/{dd}.json: all 5-minute values of the day + daily summary.
     */
    public byte[] generateArchive(
            LocalDate date,
            Instant windowEnd,
            List<ShortTermRecord> dayRecords,
            EntityRegistry entityRegistry) throws Exception {

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("date", date.toString());
        root.put("generated", windowEnd.toString());
        root.put("interval_minutes", 5);

        Map<Instant, List<ShortTermRecord>> byTimestamp = dayRecords.stream()
                .collect(Collectors.groupingBy(ShortTermRecord::startTs, TreeMap::new, Collectors.toList()));

        List<Map<String, Object>> recordsList = new ArrayList<>();
        for (Map.Entry<Instant, List<ShortTermRecord>> entry : byTimestamp.entrySet()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("timestamp", entry.getKey().toString());

            Map<String, Object> entities = new LinkedHashMap<>();
            for (ShortTermRecord rec : entry.getValue()) {
                Map<String, Object> entityData = new LinkedHashMap<>();
                entityData.put("mean", rec.mean());
                entityData.put("min", rec.min());
                entityData.put("max", rec.max());
                entityData.put("last", rec.last());
                entities.put(rec.entityId(), entityData);
            }
            record.put("entities", entities);
            recordsList.add(record);
        }
        root.put("records", recordsList);

        Map<String, List<ShortTermRecord>> byEntity = dayRecords.stream()
                .collect(Collectors.groupingBy(ShortTermRecord::entityId, LinkedHashMap::new, Collectors.toList()));

        Map<String, Object> dailySummary = new LinkedHashMap<>();
        for (Map.Entry<String, List<ShortTermRecord>> entry : byEntity.entrySet()) {
            dailySummary.put(entry.getKey(), computeDailySummary(entry.getValue()));
        }
        root.put("daily_summary", dailySummary);

        return objectMapper.writeValueAsBytes(root);
    }

    /**
     * Compute the archive path for a given date.
     */
    public static String archivePath(LocalDate date) {
        return String.format("archive/%d/%02d/%02d.json",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    private Map<String, Object> computeDailySummary(List<ShortTermRecord> records) {
        DoubleSummaryStatistics stats = records.stream()
                .filter(r -> r.mean() != null)
                .mapToDouble(ShortTermRecord::mean)
                .summaryStatistics();

        Map<String, Object> summary = new LinkedHashMap<>();
        if (stats.getCount() > 0) {
            OptionalDouble trueMin = records.stream()
                    .filter(r -> r.min() != null)
                    .mapToDouble(ShortTermRecord::min)
                    .min();
            OptionalDouble trueMax = records.stream()
                    .filter(r -> r.max() != null)
                    .mapToDouble(ShortTermRecord::max)
                    .max();

            summary.put("min", trueMin.isPresent() ? trueMin.getAsDouble() : null);
            summary.put("max", trueMax.isPresent() ? trueMax.getAsDouble() : null);
            summary.put("mean", Math.round(stats.getAverage() * 100.0) / 100.0);
            summary.put("count", (int) stats.getCount());
        } else {
            summary.put("min", null);
            summary.put("max", null);
            summary.put("mean", null);
            summary.put("count", 0);
        }
        return summary;
    }

    private String getUnit(String entityId, EntityRegistry entityRegistry) {
        return entityRegistry.getEntity(entityId)
                .map(EntityDefinition::getUnitOfMeasurement)
                .orElse(null);
    }
}
