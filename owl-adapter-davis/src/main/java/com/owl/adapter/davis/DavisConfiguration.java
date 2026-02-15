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

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("owl.adapters.davis-serial")
public class DavisConfiguration {

    private String serialPort = "SIMULATED";
    private int baudRate = 19200;
    private double latitude = 0;
    private double longitude = 0;
    private double altitude = 0;
    private int loopCount = 200;
    private int wakeupTimeoutMs = 3000;
    private int reconnectDelayMs = 5000;

    public String getSerialPort() { return serialPort; }
    public void setSerialPort(String serialPort) { this.serialPort = serialPort; }

    public int getBaudRate() { return baudRate; }
    public void setBaudRate(int baudRate) { this.baudRate = baudRate; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public int getLoopCount() { return loopCount; }
    public void setLoopCount(int loopCount) { this.loopCount = loopCount; }

    public int getWakeupTimeoutMs() { return wakeupTimeoutMs; }
    public void setWakeupTimeoutMs(int wakeupTimeoutMs) { this.wakeupTimeoutMs = wakeupTimeoutMs; }

    public int getReconnectDelayMs() { return reconnectDelayMs; }
    public void setReconnectDelayMs(int reconnectDelayMs) { this.reconnectDelayMs = reconnectDelayMs; }
}
