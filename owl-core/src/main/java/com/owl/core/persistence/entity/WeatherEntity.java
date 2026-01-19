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
package com.owl.core.persistence.entity;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * Entity registry - stores metadata about each sensor/measurement.
 * Maps to the "entities" table.
 */
@Serdeable
@MappedEntity("entities")
public class WeatherEntity {

    @Id
    @MappedProperty("entity_id")
    private String entityId;

    @MappedProperty("friendly_name")
    private String friendlyName;

    private String source;

    @MappedProperty("unit_of_measurement")
    private String unitOfMeasurement;

    @MappedProperty("device_class")
    private String deviceClass;

    @MappedProperty("state_class")
    private String stateClass;

    @MappedProperty("aggregation_method")
    private String aggregationMethod;

    @MappedProperty("created_at")
    private Instant createdAt;

    public WeatherEntity() {
    }

    public WeatherEntity(String entityId, String friendlyName, String source,
                         String unitOfMeasurement, String deviceClass, String stateClass,
                         String aggregationMethod, Instant createdAt) {
        this.entityId = entityId;
        this.friendlyName = friendlyName;
        this.source = source;
        this.unitOfMeasurement = unitOfMeasurement;
        this.deviceClass = deviceClass;
        this.stateClass = stateClass;
        this.aggregationMethod = aggregationMethod;
        this.createdAt = createdAt;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUnitOfMeasurement() {
        return unitOfMeasurement;
    }

    public void setUnitOfMeasurement(String unitOfMeasurement) {
        this.unitOfMeasurement = unitOfMeasurement;
    }

    public String getDeviceClass() {
        return deviceClass;
    }

    public void setDeviceClass(String deviceClass) {
        this.deviceClass = deviceClass;
    }

    public String getStateClass() {
        return stateClass;
    }

    public void setStateClass(String stateClass) {
        this.stateClass = stateClass;
    }

    public String getAggregationMethod() {
        return aggregationMethod;
    }

    public void setAggregationMethod(String aggregationMethod) {
        this.aggregationMethod = aggregationMethod;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Builder for creating WeatherEntity instances with sensible defaults.
     */
    public static Builder builder(String entityId, String source) {
        return new Builder(entityId, source);
    }

    public static class Builder {
        private final String entityId;
        private final String source;
        private String friendlyName;
        private String unitOfMeasurement;
        private String deviceClass;
        private String stateClass = "measurement";
        private String aggregationMethod = "mean";
        private Instant createdAt;

        private Builder(String entityId, String source) {
            this.entityId = entityId;
            this.source = source;
        }

        public Builder friendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
            return this;
        }

        public Builder unitOfMeasurement(String unitOfMeasurement) {
            this.unitOfMeasurement = unitOfMeasurement;
            return this;
        }

        public Builder deviceClass(String deviceClass) {
            this.deviceClass = deviceClass;
            return this;
        }

        public Builder stateClass(String stateClass) {
            this.stateClass = stateClass;
            return this;
        }

        public Builder aggregationMethod(String aggregationMethod) {
            this.aggregationMethod = aggregationMethod;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public WeatherEntity build() {
            return new WeatherEntity(
                    entityId,
                    friendlyName,
                    source,
                    unitOfMeasurement,
                    deviceClass,
                    stateClass,
                    aggregationMethod,
                    createdAt != null ? createdAt : Instant.now()
            );
        }
    }
}
