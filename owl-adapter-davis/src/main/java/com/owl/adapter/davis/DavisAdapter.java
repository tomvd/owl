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
import org.slf4j.Logger;

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
 * <p>
 * This adapter uses a dedicated thread for blocking serial I/O operations,
 * ensuring that serial port issues don't affect other parts of the system.
 * <p>
 * Configuration (application.yml):
 * <pre>
 * owl:
 *   adapters:
 *     davis-serial:
 *       enabled: true
 *       serial-port: COM4
 *       baud-rate: 19200
 * </pre>
 */
public class DavisAdapter implements WeatherAdapter {

    private static final String ADAPTER_NAME = "davis-serial";

    private Logger logger;
    private AdapterContext context;
    private volatile boolean running = false;
    private volatile Instant lastSuccessfulRead;

    private DavisSerialConnection serialConnection;
    private DavisProtocolHandler protocolHandler;
    private int lastNextRecord = -1;
    private Instant lastArchiveTime;

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
                // Temperature sensors
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

                // Humidity sensors
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

                // Pressure
                EntityDefinition.builder()
                        .entityId("sensor.davis_pressure")
                        .friendlyName("Barometric Pressure")
                        .source(ADAPTER_NAME)
                        .unit("hPa")
                        .deviceClass("pressure")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                // Wind sensors
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

                // Rain sensors
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

                // Solar/UV sensors
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

    @Override
    public void start(AdapterContext context) throws AdapterException {
        this.context = context;
        this.logger = context.getLogger();

        logger.info("Starting Davis Vantage Pro adapter");

        // Load configuration
        AdapterConfiguration config = context.getConfiguration();
        String serialPort = config.getRequiredString("serial-port");
        int baudRate = config.getInt("baud-rate").orElse(19200);

        logger.info("Configuration: port={}, baud={}", serialPort, baudRate);

        try {
            // Create serial connection (real or simulated)
            if (SimulatedDavisConnection.isSimulatedPort(serialPort)) {
                logger.info("Using SIMULATED connection - no hardware required");
                serialConnection = new SimulatedDavisConnection(serialPort, baudRate);
            } else {
                serialConnection = new DavisSerialConnection(serialPort, baudRate);
            }

            // Create protocol handler
            protocolHandler = new DavisProtocolHandler(serialConnection);

            // Set up callbacks
            protocolHandler.setLoopRecordCallback(this::onLoopRecord);
            protocolHandler.setArchiveRecordCallback(this::onArchiveRecord);
            protocolHandler.setStateChangeCallback(state ->
                    logger.debug("Protocol state: {}", state));
            protocolHandler.setErrorCallback(error ->
                    logger.error("Protocol error: {}", error));

            // Start the protocol handler
            protocolHandler.start();

            this.running = true;
            logger.info("Davis adapter started successfully");

        } catch (IOException e) {
            throw new AdapterException("Failed to start Davis adapter: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() throws AdapterException {
        logger.info("Stopping Davis Vantage Pro adapter");
        running = false;

        if (protocolHandler != null) {
            protocolHandler.stop();
            protocolHandler = null;
        }

        if (serialConnection != null) {
            serialConnection.close();
            serialConnection = null;
        }

        logger.info("Davis adapter stopped");
    }

    @Override
    public AdapterHealth getHealth() {
        if (!running) {
            return AdapterHealth.unhealthy("Adapter not running");
        }

        if (lastSuccessfulRead == null) {
            return AdapterHealth.degraded("No data received yet", Map.of());
        }

        // Check if data is stale (no update in 30 seconds)
        Instant staleThreshold = Instant.now().minusSeconds(30);
        if (lastSuccessfulRead.isBefore(staleThreshold)) {
            return AdapterHealth.degraded("Data is stale",
                    Map.of("lastRead", lastSuccessfulRead.toString()));
        }

        return AdapterHealth.healthy("Operating normally", lastSuccessfulRead);
    }

    @Override
    public boolean supportsRecovery() {
        // Davis consoles store archive data that can be downloaded
        return true;
    }

    @Override
    public RecoveryHandle requestRecovery(Instant fromTime, Instant toTime) throws AdapterException {
        logger.info("Recovery requested from {} to {}", fromTime, toTime);

        if (protocolHandler == null) {
            throw new AdapterException("Adapter not started");
        }

        // Start archive download asynchronously
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                protocolHandler.downloadArchive(fromTime);
            } catch (IOException e) {
                logger.error("Archive download failed", e);
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
                return java.util.Optional.empty(); // Progress unknown for Davis archive
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

        // Check for new archive record (for auto-archiving detection)
        if (lastNextRecord != -1 && lastNextRecord != record.nextRecord() && lastArchiveTime != null) {
            logger.info("Archive record changed: {} -> {}, triggering download", lastNextRecord, record.nextRecord());
            // Trigger archive download asynchronously
            // Use time from 6 minutes ago to ensure we catch the new record
            // (Davis timestamps have only minute precision, and archive interval is 5 min)
            Instant downloadFrom = lastArchiveTime.minusSeconds(360);
            CompletableFuture.runAsync(() -> {
                try {
                    protocolHandler.downloadArchive(downloadFrom);
                } catch (IOException e) {
                    logger.error("Failed to download archive after record change", e);
                }
            });
        }
        lastNextRecord = record.nextRecord();
        lastArchiveTime = now;

        // Build sensor readings - loop records are NOT persistent (transient data for live display only)
        List<SensorReading> readings = new ArrayList<>();

        // Only publish valid readings (skip zero values for sensors that can't be zero)
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

        // Publish batch
        try {
            context.getMessageBus().publishBatch(readings);
            logger.debug("Published {} readings: temp={}, humidity={}, pressure={}",
                    readings.size(), record.tempOut(), record.humidityOut(), record.pressure());
        } catch (MessageBusException e) {
            logger.error("Failed to publish readings", e);
        }
    }

    private void onArchiveRecord(com.owl.adapter.davis.protocol.DavisArchiveRecord record) {
        Instant timestamp = record.timestamp();

        // Build sensor readings from archive record
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

        // Publish batch
        try {
            context.getMessageBus().publishBatch(readings);
            logger.debug("Published {} archive readings for {}", readings.size(), timestamp);
        } catch (MessageBusException e) {
            logger.error("Failed to publish archive readings", e);
        }
    }
}
