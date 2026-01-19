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

import com.owl.core.api.*;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
 *       latitude: 51.063
 *       longitude: 4.667
 *       altitude: 32.0
 * </pre>
 * <p>
 * TODO: Full implementation pending - this is a skeleton for structure demonstration.
 * The existing DavisAdapter code from com.tomvd.adapter.davis should be migrated here.
 */
public class DavisAdapter implements WeatherAdapter {

    private static final String ADAPTER_NAME = "davis-serial";

    private Logger logger;
    private AdapterContext context;
    private volatile boolean running = false;
    private volatile Instant lastSuccessfulRead;

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

        // TODO: Implement full serial port communication
        // The existing code from com.tomvd.adapter.davis.DavisAdapter should be migrated here.
        // Key implementation points:
        // 1. Open serial port in dedicated thread
        // 2. Wake up console with \n characters
        // 3. Send LOOP command to request data packets
        // 4. Parse LOOP packets (99 bytes) with CRC validation
        // 5. Publish sensor readings to message bus
        // 6. Handle reconnection on errors

        this.running = true;
        logger.warn("Davis adapter started in SKELETON mode - full implementation pending migration");
    }

    @Override
    public void stop() throws AdapterException {
        logger.info("Stopping Davis Vantage Pro adapter");
        running = false;
        // TODO: Close serial port and stop reader thread
        logger.info("Davis adapter stopped");
    }

    @Override
    public AdapterHealth getHealth() {
        if (!running) {
            return AdapterHealth.unhealthy("Adapter not running");
        }

        if (lastSuccessfulRead == null) {
            return AdapterHealth.degraded("No data received yet (skeleton mode)", Map.of());
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
        // TODO: Implement archive download from Davis console
        // Use DMPAFT command to download archive records after a specific timestamp
        throw new AdapterException("Recovery not yet implemented");
    }
}
