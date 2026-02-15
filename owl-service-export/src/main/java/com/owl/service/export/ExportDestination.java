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
package com.owl.service.export;

import java.io.IOException;

/**
 * Abstraction for export destinations.
 * <p>
 * Implementations write generated JSON files to a specific target
 * (local filesystem, FTP server, etc.).
 */
public interface ExportDestination {

    /**
     * Write data to the given path.
     *
     * @param path relative path (e.g., "current.json" or "archive/2026/02/11.json")
     * @param data file content
     * @throws IOException if the write fails
     */
    void write(String path, byte[] data) throws IOException;
}
