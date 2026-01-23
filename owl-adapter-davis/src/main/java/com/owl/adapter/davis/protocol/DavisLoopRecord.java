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

/**
 * Immutable record representing data from a Davis Vantage Pro LOOP packet.
 * <p>
 * LOOP packets are 99 bytes and sent every 2.5 seconds containing real-time weather data.
 * All values are converted to metric units (Celsius, hPa, km/h, mm).
 *
 * @param tempOut         outside temperature in °C
 * @param tempIn          inside temperature in °C
 * @param humidityOut     outside humidity in %
 * @param humidityIn      inside humidity in %
 * @param pressure        barometric pressure in hPa
 * @param windSpeed       current wind speed in km/h
 * @param windDir         wind direction in degrees (0-360)
 * @param windGust        wind gust speed in km/h
 * @param rainRate        current rain rate in mm/h
 * @param rainDaily       daily rain total in mm
 * @param solarRadiation  solar radiation in W/m²
 * @param uvIndex         UV index (0-16)
 * @param nextRecord      pointer to next archive record (for archive detection)
 * @param consoleBattery  console battery voltage
 * @param barometerTrend  barometric trend indicator (-60 to +60)
 */
public record DavisLoopRecord(
        double tempOut,
        double tempIn,
        int humidityOut,
        int humidityIn,
        double pressure,
        double windSpeed,
        int windDir,
        double windGust,
        double rainRate,
        double rainDaily,
        int solarRadiation,
        double uvIndex,
        int nextRecord,
        double consoleBattery,
        int barometerTrend
) {
    /**
     * Create a builder for constructing DavisLoopRecord instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DavisLoopRecord.
     */
    public static class Builder {
        private double tempOut;
        private double tempIn;
        private int humidityOut;
        private int humidityIn;
        private double pressure;
        private double windSpeed;
        private int windDir;
        private double windGust;
        private double rainRate;
        private double rainDaily;
        private int solarRadiation;
        private double uvIndex;
        private int nextRecord;
        private double consoleBattery;
        private int barometerTrend;

        public Builder tempOut(double tempOut) {
            this.tempOut = tempOut;
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

        public Builder rainRate(double rainRate) {
            this.rainRate = rainRate;
            return this;
        }

        public Builder rainDaily(double rainDaily) {
            this.rainDaily = rainDaily;
            return this;
        }

        public Builder solarRadiation(int solarRadiation) {
            this.solarRadiation = solarRadiation;
            return this;
        }

        public Builder uvIndex(double uvIndex) {
            this.uvIndex = uvIndex;
            return this;
        }

        public Builder nextRecord(int nextRecord) {
            this.nextRecord = nextRecord;
            return this;
        }

        public Builder consoleBattery(double consoleBattery) {
            this.consoleBattery = consoleBattery;
            return this;
        }

        public Builder barometerTrend(int barometerTrend) {
            this.barometerTrend = barometerTrend;
            return this;
        }

        public DavisLoopRecord build() {
            return new DavisLoopRecord(
                    tempOut, tempIn, humidityOut, humidityIn,
                    pressure, windSpeed, windDir, windGust,
                    rainRate, rainDaily, solarRadiation, uvIndex,
                    nextRecord, consoleBattery, barometerTrend
            );
        }
    }
}
