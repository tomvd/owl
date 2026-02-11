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

/**
 * Aggregation method for statistics computation.
 * <p>
 * Determines how values are combined when computing statistics
 * over a time period.
 */
public enum AggregationMethod {

    /**
     * Average value (temperatures, humidity).
     * Uses weighted average when rolling up statistics.
     */
    MEAN,

    /**
     * Maximum value (wind gusts, rain rate).
     * Takes the highest value in the period.
     */
    MAX,

    /**
     * Minimum value (nearest lightning).
     * Takes the lowest value in the period.
     */
    MIN,

    /**
     * Total count (lightning strikes).
     * Sums all values in the period.
     */
    SUM,

    /**
     * Final value in period (cumulative counters like daily rain).
     * Takes the last recorded value.
     */
    LAST,

    /**
     * No numeric aggregation (non-numeric data like icons, conditions, text).
     * Only attributes are captured from the last event.
     */
    NONE
}
