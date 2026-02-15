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
 * Export destination that uploads files via FTP.
 * <p>
 * Placeholder implementation. A full implementation would use
 * Apache Commons Net FTPClient or a similar library.
 */
public class FtpDestination implements ExportDestination {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String directory;

    public FtpDestination(String host, int port, String username, String password, String directory) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.directory = directory;
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        // TODO: Implement FTP upload using Apache Commons Net or similar
        throw new UnsupportedOperationException("FTP destination not yet implemented");
    }
}
