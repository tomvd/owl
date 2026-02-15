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

import com.owl.adapter.davis.protocol.DavisLoopRecord;
import com.owl.adapter.davis.protocol.DavisProtocolHandler;
import com.owl.adapter.davis.serial.DavisSerialConnection;
import com.owl.adapter.davis.serial.SimulatedDavisConnection;
import com.owl.core.api.*;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Davis Vantage Pro weather adapter implementation.
 * <p>
 * Connects to a Davis Vantage Pro console via serial port and reads
 * LOOP packets every 2.5 seconds. Also supports archive data download
 * for recovery after downtime.
 */
@Singleton
@Requires(property = "owl.adapters.davis-serial.enabled", value = "true")
public class DavisAdapter implements WeatherAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DavisAdapter.class);
    private static final String ADAPTER_NAME = "davis-serial";

    private final MessageBus messageBus;
    private final DavisConfiguration config;

    private volatile boolean running = false;
    private volatile Instant lastSuccessfulRead;

    private DavisSerialConnection serialConnection;
    private DavisProtocolHandler protocolHandler;
    private int lastNextRecord = -1;
    private Instant lastArchiveTime;

    public DavisAdapter(MessageBus messageBus, DavisConfiguration config) {
        this.messageBus = messageBus;
        this.config = config;
    }

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }

    @Override
    public String getDisplayName() {
        return "Davis Vantage Pro Adapter";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<EntityDefinition> getProvidedEntities() {
        return List.of(
                EntityDefinition.builder()
                        .entityId("sensor.davis_temp_out")
                        .friendlyName("Outside Temperature")
                        .source(ADAPTER_NAME)
                        .unit("°C")
                        .deviceClass("temperature")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_temp_in")
                        .friendlyName("Inside Temperature")
                        .source(ADAPTER_NAME)
                        .unit("°C")
                        .deviceClass("temperature")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_humidity_out")
                        .friendlyName("Outside Humidity")
                        .source(ADAPTER_NAME)
                        .unit("%")
                        .deviceClass("humidity")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_humidity_in")
                        .friendlyName("Inside Humidity")
                        .source(ADAPTER_NAME)
                        .unit("%")
                        .deviceClass("humidity")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_pressure")
                        .friendlyName("Barometric Pressure")
                        .source(ADAPTER_NAME)
                        .unit("hPa")
                        .deviceClass("pressure")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_wind_speed")
                        .friendlyName("Wind Speed")
                        .source(ADAPTER_NAME)
                        .unit("km/h")
                        .deviceClass("wind_speed")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_wind_direction")
                        .friendlyName("Wind Direction")
                        .source(ADAPTER_NAME)
                        .unit("°")
                        .deviceClass("wind_direction")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_wind_gust")
                        .friendlyName("Wind Gust")
                        .source(ADAPTER_NAME)
                        .unit("km/h")
                        .deviceClass("wind_speed")
                        .aggregation(AggregationMethod.MAX)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_rain_rate")
                        .friendlyName("Rain Rate")
                        .source(ADAPTER_NAME)
                        .unit("mm/h")
                        .deviceClass("precipitation_intensity")
                        .aggregation(AggregationMethod.MAX)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_rain_daily")
                        .friendlyName("Daily Rain")
                        .source(ADAPTER_NAME)
                        .unit("mm")
                        .deviceClass("precipitation")
                        .aggregation(AggregationMethod.LAST)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_solar_radiation")
                        .friendlyName("Solar Radiation")
                        .source(ADAPTER_NAME)
                        .unit("W/m²")
                        .deviceClass("irradiance")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.davis_uv_index")
                        .friendlyName("UV Index")
                        .source(ADAPTER_NAME)
                        .unit("")
                        .deviceClass("uv_index")
                        .aggregation(AggregationMethod.MAX)
                        .build()
        );
    }

    @PostConstruct
    void start() {
        LOG.info("Starting Davis Vantage Pro adapter");

        String serialPort = config.getSerialPort();
        int baudRate = config.getBaudRate();

        LOG.info("Configuration: port={}, baud={}", serialPort, baudRate);

        try {
            if (SimulatedDavisConnection.isSimulatedPort(serialPort)) {
                LOG.info("Using SIMULATED connection - no hardware required");
                serialConnection = new SimulatedDavisConnection(serialPort, baudRate);
            } else {
                serialConnection = new DavisSerialConnection(serialPort, baudRate);
            }

            protocolHandler = new DavisProtocolHandler(serialConnection);

            protocolHandler.setLoopRecordCallback(this::onLoopRecord);
            protocolHandler.setArchiveRecordCallback(this::onArchiveRecord);
            protocolHandler.setStateChangeCallback(state ->
                    LOG.debug("Protocol state: {}", state));
            protocolHandler.setErrorCallback(error ->
                    LOG.error("Protocol error: {}", error));

            protocolHandler.start();

            this.running = true;
            LOG.info("Davis adapter started successfully");

        } catch (IOException e) {
            throw new RuntimeException("Failed to start Davis adapter: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void stop() {
        LOG.info("Stopping Davis Vantage Pro adapter");
        running = false;

        if (protocolHandler != null) {
            protocolHandler.stop();
            protocolHandler = null;
        }

        if (serialConnection != null) {
            serialConnection.close();
            serialConnection = null;
        }

        LOG.info("Davis adapter stopped");
    }

    @Override
    public AdapterHealth getHealth() {
        if (!running) {
            return AdapterHealth.unhealthy("Adapter not running");
        }

        if (lastSuccessfulRead == null) {
            return AdapterHealth.degraded("No data received yet", Map.of());
        }

        Instant staleThreshold = Instant.now().minusSeconds(30);
        if (lastSuccessfulRead.isBefore(staleThreshold)) {
            return AdapterHealth.degraded("Data is stale",
                    Map.of("lastRead", lastSuccessfulRead.toString()));
        }

        return AdapterHealth.healthy("Operating normally", lastSuccessfulRead);
    }

    @Override
    public boolean supportsRecovery() {
        return true;
    }

    @Override
    public RecoveryHandle requestRecovery(Instant fromTime, Instant toTime) throws AdapterException {
        LOG.info("Recovery requested from {} to {}", fromTime, toTime);

        if (protocolHandler == null) {
            throw new AdapterException("Adapter not started");
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                protocolHandler.downloadArchive(fromTime);
            } catch (IOException e) {
                LOG.error("Archive download failed", e);
                throw new RuntimeException(e);
            }
        });

        return new RecoveryHandle() {
            private volatile long recordsRecovered = 0;

            @Override
            public State getState() {
                if (!future.isDone()) {
                    return State.RUNNING;
                }
                if (future.isCompletedExceptionally()) {
                    return State.FAILED;
                }
                return State.COMPLETED;
            }

            @Override
            public java.util.Optional<Integer> getProgress() {
                return java.util.Optional.empty();
            }

            @Override
            public long getRecordsRecovered() {
                return recordsRecovered;
            }

            @Override
            public java.util.Optional<String> getError() {
                if (future.isCompletedExceptionally()) {
                    try {
                        future.join();
                    } catch (Exception e) {
                        return java.util.Optional.of(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    }
                }
                return java.util.Optional.empty();
            }

            @Override
            public void cancel() {
                future.cancel(true);
            }

            @Override
            public void await() throws InterruptedException {
                try {
                    future.join();
                } catch (java.util.concurrent.CancellationException e) {
                    throw new InterruptedException("Recovery cancelled");
                }
            }
        };
    }

    // ==================== Record Callbacks ====================

    private void onLoopRecord(DavisLoopRecord record) {
        Instant now = Instant.now();
        lastSuccessfulRead = now;

        if (lastNextRecord != -1 && lastNextRecord != record.nextRecord() && lastArchiveTime != null) {
            LOG.info("Archive record changed: {} -> {}, triggering download", lastNextRecord, record.nextRecord());
            Instant downloadFrom = lastArchiveTime.minusSeconds(360);
            CompletableFuture.runAsync(() -> {
                try {
                    protocolHandler.downloadArchive(downloadFrom);
                } catch (IOException e) {
                    LOG.error("Failed to download archive after record change", e);
                }
            });
        }
        lastNextRecord = record.nextRecord();
        lastArchiveTime = now;

        List<SensorReading> readings = new ArrayList<>();

        readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_temp_out", record.tempOut(), null, false));
        readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_temp_in", record.tempIn(), null, false));

        if (record.humidityOut() > 0) {
            readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_humidity_out", record.humidityOut(), null, false));
        }
        if (record.humidityIn() > 0) {
            readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_humidity_in", record.humidityIn(), null, false));
        }

        readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_pressure", record.pressure(), null, false));
        readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_wind_speed", record.windSpeed(), null, false));

        if (record.windDir() >= 0 && record.windDir() <= 360) {
            readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_wind_direction", record.windDir(), null, false));
        }

        readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_wind_gust", record.windGust(), null, false));
        readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_rain_rate", record.rainRate(), null, false));
        readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_rain_daily", record.rainDaily(), null, false));

        if (record.solarRadiation() > 0) {
            readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_solar_radiation", record.solarRadiation(), null, false));
        }
        if (record.uvIndex() > 0) {
            readings.add(new SensorReading(now, ADAPTER_NAME, "sensor.davis_uv_index", record.uvIndex(), null, false));
        }

        try {
            messageBus.publishBatch(readings);
            LOG.debug("Published {} readings: temp={}, humidity={}, pressure={}",
                    readings.size(), record.tempOut(), record.humidityOut(), record.pressure());
        } catch (MessageBusException e) {
            LOG.error("Failed to publish readings", e);
        }
    }

    private void onArchiveRecord(com.owl.adapter.davis.protocol.DavisArchiveRecord record) {
        Instant timestamp = record.timestamp();

        List<SensorReading> readings = new ArrayList<>();

        readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_temp_out", record.tempOut()));
        readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_temp_in", record.tempIn()));

        if (record.humidityOut() > 0) {
            readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_humidity_out", record.humidityOut()));
        }
        if (record.humidityIn() > 0) {
            readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_humidity_in", record.humidityIn()));
        }

        readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_pressure", record.pressure()));
        readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_wind_speed", record.windSpeed()));

        if (record.windDir() >= 0 && record.windDir() <= 360) {
            readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_wind_direction", record.windDir()));
        }

        readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_wind_gust", record.windGust()));
        readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_rain_rate", record.rainRate()));
        readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_rain_daily", record.rain()));

        if (record.solarRadiation() > 0) {
            readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_solar_radiation", record.solarRadiation()));
        }
        if (record.uvIndex() > 0) {
            readings.add(new SensorReading(timestamp, ADAPTER_NAME, "sensor.davis_uv_index", record.uvIndex()));
        }

        try {
            messageBus.publishBatch(readings);
            LOG.debug("Published {} archive readings for {}", readings.size(), timestamp);
        } catch (MessageBusException e) {
            LOG.error("Failed to publish archive readings", e);
        }
    }
}
