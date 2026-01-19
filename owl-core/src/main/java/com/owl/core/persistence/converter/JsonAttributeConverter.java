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
 */
@Singleton
public class JsonAttributeConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final ObjectMapper objectMapper;

    public JsonAttributeConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToPersistedValue(Map<String, Object> entityValue, ConversionContext context) {
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
    public Map<String, Object> convertToEntityValue(String persistedValue, ConversionContext context) {
        if (persistedValue == null || persistedValue.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(persistedValue, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse attributes from JSON", e);
        }
    }
}
