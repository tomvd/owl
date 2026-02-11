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
package com.owl.core.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Converts between Map&lt;String, Object&gt; and PostgreSQL JSONB.
 * <p>
 * Handles both String input (when JDBC returns raw JSON) and Map input
 * (when JDBC driver pre-parses the JSONB column).
 */
@Singleton
public class JsonAttributeConverter implements AttributeConverter<Map<String, Object>, Object> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final ObjectMapper objectMapper;

    public JsonAttributeConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object convertToPersistedValue(Map<String, Object> entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(entityValue);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert attributes to JSON", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToEntityValue(Object persistedValue, ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }

        // Handle case where JDBC driver already parsed JSONB to Map
        if (persistedValue instanceof Map) {
            return (Map<String, Object>) persistedValue;
        }

        // Handle case where JDBC returns raw JSON string
        if (persistedValue instanceof String stringValue) {
            if (stringValue.isEmpty()) {
                return null;
            }
            try {
                return objectMapper.readValue(stringValue, MAP_TYPE);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse attributes from JSON", e);
            }
        }

        throw new RuntimeException("Unexpected type for JSON attributes: " + persistedValue.getClass());
    }
}
