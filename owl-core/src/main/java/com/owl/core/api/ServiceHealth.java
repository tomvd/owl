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
 * Health status of a service.
 * <p>
 * Used by the framework for monitoring and health checks.
 */
public final class ServiceHealth {

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
    private final Instant lastActivity;

    public ServiceHealth(
            Status status,
            String message,
            Map<String, Object> details,
            Instant lastActivity) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = message;
        this.details = details != null ? Map.copyOf(details) : Map.of();
        this.lastActivity = lastActivity;
    }

    /**
     * Create a healthy status.
     *
     * @param message status message
     * @return healthy status
     */
    public static ServiceHealth healthy(String message) {
        return new ServiceHealth(Status.HEALTHY, message, null, Instant.now());
    }

    /**
     * Create a healthy status with last activity time.
     *
     * @param message      status message
     * @param lastActivity time of last activity
     * @return healthy status
     */
    public static ServiceHealth healthy(String message, Instant lastActivity) {
        return new ServiceHealth(Status.HEALTHY, message, null, lastActivity);
    }

    /**
     * Create a degraded status.
     *
     * @param message status message
     * @param details additional details
     * @return degraded status
     */
    public static ServiceHealth degraded(String message, Map<String, Object> details) {
        return new ServiceHealth(Status.DEGRADED, message, details, null);
    }

    /**
     * Create an unhealthy status.
     *
     * @param message status message
     * @return unhealthy status
     */
    public static ServiceHealth unhealthy(String message) {
        return new ServiceHealth(Status.UNHEALTHY, message, null, null);
    }

    /**
     * Create an unknown status.
     *
     * @param message status message
     * @return unknown status
     */
    public static ServiceHealth unknown(String message) {
        return new ServiceHealth(Status.UNKNOWN, message, null, null);
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

    public Optional<Instant> getLastActivity() {
        return Optional.ofNullable(lastActivity);
    }

    public boolean isHealthy() {
        return status == Status.HEALTHY;
    }

    @Override
    public String toString() {
        return "ServiceHealth{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", lastActivity=" + lastActivity +
                '}';
    }
}
