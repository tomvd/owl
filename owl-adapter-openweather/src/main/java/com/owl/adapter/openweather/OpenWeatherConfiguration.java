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

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("owl.adapters.openweather")
public class OpenWeatherConfiguration {

    private double latitude;
    private double longitude;
    private String units = "metric";
    private String lang = "en";
    private int pollIntervalMinutes = 10;
    private String apiUrl = "https://api.openweathermap.org/data/2.5/weather";

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public int getPollIntervalMinutes() { return pollIntervalMinutes; }
    public void setPollIntervalMinutes(int pollIntervalMinutes) { this.pollIntervalMinutes = pollIntervalMinutes; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
}
