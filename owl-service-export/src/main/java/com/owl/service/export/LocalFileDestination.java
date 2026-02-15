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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Export destination that writes files to the local filesystem.
 */
public class LocalFileDestination implements ExportDestination {

    private final Path baseDirectory;

    public LocalFileDestination(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        Path target = baseDirectory.resolve(path);
        Files.createDirectories(target.getParent());
        Files.write(target, data,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }
}
