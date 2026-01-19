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
package com.owl.adapter.davis;

import com.owl.core.api.AdapterProvider;
import com.owl.core.api.ProviderMetadata;
import com.owl.core.api.WeatherAdapter;

/**
 * Provider for Davis Vantage Pro weather adapter.
 * <p>
 * This is registered via ServiceLoader.
 * <p>
 * File: META-INF/services/com.owl.core.api.AdapterProvider
 * Contents: com.owl.adapter.davis.DavisAdapterProvider
 */
public class DavisAdapterProvider implements AdapterProvider {

    @Override
    public WeatherAdapter createAdapter() {
        return new DavisAdapter();
    }

    @Override
    public ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "davis-serial",
                "Davis Vantage Pro Adapter",
                "1.0.0",
                "Owl (OpenWeatherLink) Project",
                "Connects to Davis Vantage Pro weather stations via serial port"
        );
    }
}
