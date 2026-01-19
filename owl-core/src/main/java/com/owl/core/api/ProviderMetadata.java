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
 * Metadata about an adapter provider.
 *
 * @param id          Unique identifier for this adapter
 * @param name        Human-readable name
 * @param version     Version string (semantic versioning recommended)
 * @param author      Author or organization name
 * @param description Brief description of what this adapter does
 */
public record ProviderMetadata(
        String id,
        String name,
        String version,
        String author,
        String description
) {
    @Override
    public String toString() {
        return String.format("%s v%s (%s) by %s", name, version, id, author);
    }
}
