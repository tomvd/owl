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

import com.owl.core.api.OwlService;
import com.owl.core.api.ServiceHealth;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Export service health bean.
 * <p>
 * The actual export work is done by {@link CurrentExporter},
 * {@link TwentyFourHourExporter}, and {@link ArchiveExporter}.
 * This bean provides service identity and health status.
 */
@Singleton
@Requires(property = "owl.services.export.enabled", value = "true")
public class ExportService implements OwlService {

    @Override
    public String getName() {
        return "export";
    }

    @Override
    public String getDisplayName() {
        return "Data Export Service";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public ServiceHealth getHealth() {
        return ServiceHealth.healthy("Export service operational");
    }
}
