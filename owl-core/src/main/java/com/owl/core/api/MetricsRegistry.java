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

import java.util.Map;
import java.util.function.Supplier;

/**
 * Metrics registry for reporting adapter metrics.
 * <p>
 * Adapters can use this to record metrics that will be exposed
 * via the application's metrics endpoint.
 */
public interface MetricsRegistry {

    /**
     * Increment a counter metric by 1.
     *
     * @param name metric name
     */
    void incrementCounter(String name);

    /**
     * Increment a counter by a specific amount.
     *
     * @param name   metric name
     * @param amount amount to increment
     */
    void incrementCounter(String name, long amount);

    /**
     * Increment a counter with tags.
     *
     * @param name metric name
     * @param tags metric tags
     */
    void incrementCounter(String name, Map<String, String> tags);

    /**
     * Record a gauge value.
     * <p>
     * Gauges represent point-in-time values that can go up or down.
     *
     * @param name  metric name
     * @param value gauge value
     */
    void recordGauge(String name, double value);

    /**
     * Register a gauge with a supplier.
     * <p>
     * The supplier will be called whenever the metric is sampled.
     *
     * @param name     metric name
     * @param supplier value supplier
     */
    void registerGauge(String name, Supplier<Number> supplier);

    /**
     * Record a timing measurement.
     *
     * @param name       metric name
     * @param durationMs duration in milliseconds
     */
    void recordTiming(String name, long durationMs);

    /**
     * Record a distribution sample.
     * <p>
     * Used for tracking distributions of values (e.g., response times).
     *
     * @param name  metric name
     * @param value sample value
     */
    void recordDistribution(String name, double value);
}
