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
package com.owl.adapter.davis.protocol;

import java.time.Instant;

/**
 * Immutable record representing data from a Davis Vantage Pro archive record.
 * <p>
 * Archive records are 52 bytes each, stored in 267-byte pages (5 records per page + 1 byte sequence + 4 bytes CRC).
 * They contain 5-minute averaged/accumulated weather data.
 * All values are converted to metric units.
 *
 * @param timestamp       timestamp of the archive record
 * @param tempOut         average outside temperature in °C
 * @param tempOutHigh     high outside temperature in °C
 * @param tempOutLow      low outside temperature in °C
 * @param tempIn          average inside temperature in °C
 * @param humidityOut     outside humidity in %
 * @param humidityIn      inside humidity in %
 * @param pressure        barometric pressure in hPa
 * @param windSpeed       average wind speed in km/h
 * @param windDir         prevailing wind direction in degrees
 * @param windGust        wind gust speed in km/h
 * @param windGustDir     wind gust direction in degrees
 * @param rain            rain accumulated during interval in mm
 * @param rainRate        high rain rate during interval in mm/h
 * @param solarRadiation  average solar radiation in W/m²
 * @param solarRadHigh    high solar radiation in W/m²
 * @param uvIndex         average UV index
 * @param uvIndexHigh     high UV index
 * @param et              evapotranspiration in mm
 */
public record DavisArchiveRecord(
        Instant timestamp,
        double tempOut,
        double tempOutHigh,
        double tempOutLow,
        double tempIn,
        int humidityOut,
        int humidityIn,
        double pressure,
        double windSpeed,
        int windDir,
        double windGust,
        int windGustDir,
        double rain,
        double rainRate,
        int solarRadiation,
        int solarRadHigh,
        double uvIndex,
        double uvIndexHigh,
        double et
) {
    /**
     * Create a builder for constructing DavisArchiveRecord instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DavisArchiveRecord.
     */
    public static class Builder {
        private Instant timestamp;
        private double tempOut;
        private double tempOutHigh;
        private double tempOutLow;
        private double tempIn;
        private int humidityOut;
        private int humidityIn;
        private double pressure;
        private double windSpeed;
        private int windDir;
        private double windGust;
        private int windGustDir;
        private double rain;
        private double rainRate;
        private int solarRadiation;
        private int solarRadHigh;
        private double uvIndex;
        private double uvIndexHigh;
        private double et;

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder tempOut(double tempOut) {
            this.tempOut = tempOut;
            return this;
        }

        public Builder tempOutHigh(double tempOutHigh) {
            this.tempOutHigh = tempOutHigh;
            return this;
        }

        public Builder tempOutLow(double tempOutLow) {
            this.tempOutLow = tempOutLow;
            return this;
        }

        public Builder tempIn(double tempIn) {
            this.tempIn = tempIn;
            return this;
        }

        public Builder humidityOut(int humidityOut) {
            this.humidityOut = humidityOut;
            return this;
        }

        public Builder humidityIn(int humidityIn) {
            this.humidityIn = humidityIn;
            return this;
        }

        public Builder pressure(double pressure) {
            this.pressure = pressure;
            return this;
        }

        public Builder windSpeed(double windSpeed) {
            this.windSpeed = windSpeed;
            return this;
        }

        public Builder windDir(int windDir) {
            this.windDir = windDir;
            return this;
        }

        public Builder windGust(double windGust) {
            this.windGust = windGust;
            return this;
        }

        public Builder windGustDir(int windGustDir) {
            this.windGustDir = windGustDir;
            return this;
        }

        public Builder rain(double rain) {
            this.rain = rain;
            return this;
        }

        public Builder rainRate(double rainRate) {
            this.rainRate = rainRate;
            return this;
        }

        public Builder solarRadiation(int solarRadiation) {
            this.solarRadiation = solarRadiation;
            return this;
        }

        public Builder solarRadHigh(int solarRadHigh) {
            this.solarRadHigh = solarRadHigh;
            return this;
        }

        public Builder uvIndex(double uvIndex) {
            this.uvIndex = uvIndex;
            return this;
        }

        public Builder uvIndexHigh(double uvIndexHigh) {
            this.uvIndexHigh = uvIndexHigh;
            return this;
        }

        public Builder et(double et) {
            this.et = et;
            return this;
        }

        public DavisArchiveRecord build() {
            return new DavisArchiveRecord(
                    timestamp, tempOut, tempOutHigh, tempOutLow, tempIn,
                    humidityOut, humidityIn, pressure,
                    windSpeed, windDir, windGust, windGustDir,
                    rain, rainRate,
                    solarRadiation, solarRadHigh,
                    uvIndex, uvIndexHigh, et
            );
        }
    }
}
