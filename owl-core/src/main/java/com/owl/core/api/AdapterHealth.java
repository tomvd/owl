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
import java.util.Objects;
import java.util.Optional;

/**
 * Health status of an adapter.
 * <p>
 * Used by the framework for monitoring and health checks.
 */
public final class AdapterHealth {

    /**
     * Health status levels.
     */
    public enum Status {
        /** Operating normally. */
        HEALTHY,
        /** Partially operational. */
        DEGRADED,
        /** Not functioning. */
        UNHEALTHY,
        /** Status cannot be determined. */
        UNKNOWN
    }

    private final Status status;
    private final String message;
    private final Map<String, Object> details;
    private final Instant lastSuccessfulRead;

    public AdapterHealth(
            Status status,
            String message,
            Map<String, Object> details,
            Instant lastSuccessfulRead) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = message;
        this.details = details != null ? Map.copyOf(details) : Map.of();
        this.lastSuccessfulRead = lastSuccessfulRead;
    }

    /**
     * Create a healthy status.
     *
     * @param message status message
     * @return healthy status
     */
    public static AdapterHealth healthy(String message) {
        return new AdapterHealth(Status.HEALTHY, message, null, Instant.now());
    }

    /**
     * Create a healthy status with last read time.
     *
     * @param message             status message
     * @param lastSuccessfulRead  time of last successful read
     * @return healthy status
     */
    public static AdapterHealth healthy(String message, Instant lastSuccessfulRead) {
        return new AdapterHealth(Status.HEALTHY, message, null, lastSuccessfulRead);
    }

    /**
     * Create a degraded status.
     *
     * @param message status message
     * @param details additional details
     * @return degraded status
     */
    public static AdapterHealth degraded(String message, Map<String, Object> details) {
        return new AdapterHealth(Status.DEGRADED, message, details, null);
    }

    /**
     * Create an unhealthy status.
     *
     * @param message status message
     * @return unhealthy status
     */
    public static AdapterHealth unhealthy(String message) {
        return new AdapterHealth(Status.UNHEALTHY, message, null, null);
    }

    /**
     * Create an unknown status.
     *
     * @param message status message
     * @return unknown status
     */
    public static AdapterHealth unknown(String message) {
        return new AdapterHealth(Status.UNKNOWN, message, null, null);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Optional<Instant> getLastSuccessfulRead() {
        return Optional.ofNullable(lastSuccessfulRead);
    }

    public boolean isHealthy() {
        return status == Status.HEALTHY;
    }

    @Override
    public String toString() {
        return "AdapterHealth{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", lastSuccessfulRead=" + lastSuccessfulRead +
                '}';
    }
}
