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

import java.time.Instant;
import java.util.Map;

/**
 * DTO for short-term (5-minute) statistics records.
 * <p>
 * Mirrors the persistence entity without database annotations,
 * keeping the API module free of Micronaut Data dependencies.
 */
public record ShortTermRecord(
        Instant startTs,
        String entityId,
        Double mean,
        Double min,
        Double max,
        Double last,
        Double sum,
        Integer count,
        Map<String, Object> attributes
) {
}
