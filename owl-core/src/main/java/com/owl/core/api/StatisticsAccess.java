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
import java.util.List;

/**
 * Interface for querying short-term statistics records.
 * <p>
 * Used by services (e.g., export) that need access to aggregated statistics
 * without depending on the persistence layer directly.
 */
public interface StatisticsAccess {

    /**
     * Get all short-term statistics within a time range.
     *
     * @param start start of range (inclusive)
     * @param end   end of range (exclusive)
     * @return list of records ordered by timestamp
     */
    List<ShortTermRecord> getShortTermStatistics(Instant start, Instant end);

    /**
     * Get short-term statistics for a specific entity within a time range.
     *
     * @param entityId entity ID
     * @param start    start of range (inclusive)
     * @param end      end of range (exclusive)
     * @return list of records ordered by timestamp
     */
    List<ShortTermRecord> getShortTermStatistics(String entityId, Instant start, Instant end);
}
