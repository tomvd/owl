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

import java.util.Objects;

/**
 * Entity definition describing a sensor/measurement.
 * <p>
 * Each entity represents a specific type of measurement that can be recorded.
 * Entities have metadata that controls how they are displayed and aggregated.
 */
public final class EntityDefinition {

    private final String entityId;
    private final String friendlyName;
    private final String source;
    private final String unitOfMeasurement;
    private final String deviceClass;
    private final String stateClass;
    private final AggregationMethod aggregationMethod;

    public EntityDefinition(
            String entityId,
            String friendlyName,
            String source,
            String unitOfMeasurement,
            String deviceClass,
            String stateClass,
            AggregationMethod aggregationMethod) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.friendlyName = Objects.requireNonNull(friendlyName, "friendlyName");
        this.source = Objects.requireNonNull(source, "source");
        this.unitOfMeasurement = unitOfMeasurement;
        this.deviceClass = deviceClass;
        this.stateClass = stateClass != null ? stateClass : "measurement";
        this.aggregationMethod = aggregationMethod != null ? aggregationMethod : AggregationMethod.MEAN;
    }

    /**
     * Create a new builder for EntityDefinition.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getEntityId() {
        return entityId;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getSource() {
        return source;
    }

    public String getUnitOfMeasurement() {
        return unitOfMeasurement;
    }

    public String getDeviceClass() {
        return deviceClass;
    }

    public String getStateClass() {
        return stateClass;
    }

    public AggregationMethod getAggregationMethod() {
        return aggregationMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityDefinition that = (EntityDefinition) o;
        return Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }

    @Override
    public String toString() {
        return "EntityDefinition{" +
                "entityId='" + entityId + '\'' +
                ", friendlyName='" + friendlyName + '\'' +
                ", source='" + source + '\'' +
                ", unit='" + unitOfMeasurement + '\'' +
                ", aggregation=" + aggregationMethod +
                '}';
    }

    /**
     * Builder for EntityDefinition.
     */
    public static class Builder {
        private String entityId;
        private String friendlyName;
        private String source;
        private String unitOfMeasurement;
        private String deviceClass = "sensor";
        private String stateClass = "measurement";
        private AggregationMethod aggregationMethod = AggregationMethod.MEAN;

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder friendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder unit(String unit) {
            this.unitOfMeasurement = unit;
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

        public Builder aggregation(AggregationMethod method) {
            this.aggregationMethod = method;
            return this;
        }

        public EntityDefinition build() {
            return new EntityDefinition(
                    entityId, friendlyName, source, unitOfMeasurement,
                    deviceClass, stateClass, aggregationMethod
            );
        }
    }
}
