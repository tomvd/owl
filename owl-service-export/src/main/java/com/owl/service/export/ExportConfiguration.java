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

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties("owl.services.export")
public class ExportConfiguration {

    private boolean enabled = false;
    private String outputDirectory = "./export";
    private boolean ftpEnabled = false;
    private String ftpHost;
    private int ftpPort = 21;
    private String ftpUsername;
    private String ftpPassword;
    private String ftpDirectory = "/";
    private String entities24h = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }

    public boolean isFtpEnabled() { return ftpEnabled; }
    public void setFtpEnabled(boolean ftpEnabled) { this.ftpEnabled = ftpEnabled; }

    public String getFtpHost() { return ftpHost; }
    public void setFtpHost(String ftpHost) { this.ftpHost = ftpHost; }

    public int getFtpPort() { return ftpPort; }
    public void setFtpPort(int ftpPort) { this.ftpPort = ftpPort; }

    public String getFtpUsername() { return ftpUsername; }
    public void setFtpUsername(String ftpUsername) { this.ftpUsername = ftpUsername; }

    public String getFtpPassword() { return ftpPassword; }
    public void setFtpPassword(String ftpPassword) { this.ftpPassword = ftpPassword; }

    public String getFtpDirectory() { return ftpDirectory; }
    public void setFtpDirectory(String ftpDirectory) { this.ftpDirectory = ftpDirectory; }

    public String getEntities24h() { return entities24h; }
    public void setEntities24h(String entities24h) { this.entities24h = entities24h; }

    public Set<String> getEntities24hSet() {
        if (entities24h == null || entities24h.isBlank()) {
            return Set.of();
        }
        return Set.of(entities24h.split(","));
    }
}
