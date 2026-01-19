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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SensorReading.
 */
class SensorReadingTest {

    @Test
    void createBasicSensorReading() {
        Instant timestamp = Instant.now();
        SensorReading reading = new SensorReading(
                timestamp,
                "test-source",
                "sensor.test_temp",
                21.5
        );

        assertEquals(timestamp, reading.getTimestamp());
        assertEquals("test-source", reading.getSource());
        assertEquals("sensor.test_temp", reading.getEntityId());
        assertEquals(21.5, reading.getValue());
        assertEquals("sensor_reading", reading.getEventType());
        assertTrue(reading.getAttributes().isEmpty());
    }

    @Test
    void createSensorReadingWithAttributes() {
        Instant timestamp = Instant.now();
        Map<String, Object> attrs = Map.of("quality", "good", "sensor_id", 42);

        SensorReading reading = new SensorReading(
                timestamp,
                "test-source",
                "sensor.test_temp",
                21.5,
                attrs
        );

        assertEquals(2, reading.getAttributes().size());
        assertEquals("good", reading.getAttributes().get("quality"));
        assertEquals(42, reading.getAttributes().get("sensor_id"));
    }

    @Test
    void sensorReadingIsImmutable() {
        Instant timestamp = Instant.now();
        Map<String, Object> attrs = Map.of("key", "value");

        SensorReading reading = new SensorReading(
                timestamp,
                "test-source",
                "sensor.test_temp",
                21.5,
                attrs
        );

        // Attributes should be immutable
        assertThrows(UnsupportedOperationException.class, () ->
                reading.getAttributes().put("new_key", "new_value")
        );
    }

    @Test
    void sensorReadingEquality() {
        Instant timestamp = Instant.now();

        SensorReading reading1 = new SensorReading(
                timestamp,
                "test-source",
                "sensor.test_temp",
                21.5
        );

        SensorReading reading2 = new SensorReading(
                timestamp,
                "test-source",
                "sensor.test_temp",
                21.5
        );

        assertEquals(reading1, reading2);
        assertEquals(reading1.hashCode(), reading2.hashCode());
    }

    @Test
    void nullTimestampThrowsException() {
        assertThrows(NullPointerException.class, () ->
                new SensorReading(null, "source", "entity", 1.0)
        );
    }

    @Test
    void nullSourceThrowsException() {
        assertThrows(NullPointerException.class, () ->
                new SensorReading(Instant.now(), null, "entity", 1.0)
        );
    }

    @Test
    void nullEntityIdThrowsException() {
        assertThrows(NullPointerException.class, () ->
                new SensorReading(Instant.now(), "source", null, 1.0)
        );
    }
}
