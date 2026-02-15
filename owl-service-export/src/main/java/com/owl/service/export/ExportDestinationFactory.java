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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Factory
@Requires(property = "owl.services.export.enabled", value = "true")
public class ExportDestinationFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ExportDestinationFactory.class);

    @Singleton
    public List<ExportDestination> exportDestinations(ExportConfiguration config) {
        List<ExportDestination> destinations = new ArrayList<>();

        destinations.add(new LocalFileDestination(Path.of(config.getOutputDirectory())));
        LOG.info("Local export directory: {}", config.getOutputDirectory());

        if (config.isFtpEnabled()) {
            destinations.add(new FtpDestination(
                    config.getFtpHost(),
                    config.getFtpPort(),
                    config.getFtpUsername(),
                    config.getFtpPassword(),
                    config.getFtpDirectory()));
            LOG.info("FTP export enabled: {}:{}{}", config.getFtpHost(), config.getFtpPort(), config.getFtpDirectory());
        }

        return destinations;
    }
}
