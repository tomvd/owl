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
package com.owl.adapter.openweather;

import com.owl.core.api.AdapterProvider;
import com.owl.core.api.ProviderMetadata;
import com.owl.core.api.WeatherAdapter;

/**
 * Provider for OpenWeather API adapter.
 * <p>
 * This is registered via ServiceLoader.
 * <p>
 * File: META-INF/services/com.owl.core.api.AdapterProvider
 * Contents: com.owl.adapter.openweather.OpenWeatherAdapterProvider
 */
public class OpenWeatherAdapterProvider implements AdapterProvider {

    @Override
    public WeatherAdapter createAdapter() {
        return new OpenWeatherAdapter();
    }

    @Override
    public ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "openweather",
                "OpenWeather API Adapter",
                "1.0.0",
                "Owl (OpenWeatherLink) Project",
                "Polls current weather data from OpenWeatherMap API"
        );
    }
}
