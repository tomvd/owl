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
 * Primary interface that all Owl services must implement.
 * <p>
 * Services are Micronaut beans annotated with {@code @Singleton} and
 * {@code @Requires(property = "owl.services.{name}.enabled", value = "true")}.
 * Lifecycle is managed via {@code @PostConstruct} and {@code @PreDestroy}.
 */
public interface OwlService {

    /**
     * Unique identifier for this service.
     */
    String getName();

    /**
     * Human-readable display name.
     */
    String getDisplayName();

    /**
     * Version of this service implementation.
     */
    String getVersion();

    /**
     * Get current health status of the service.
     */
    ServiceHealth getHealth();
}
