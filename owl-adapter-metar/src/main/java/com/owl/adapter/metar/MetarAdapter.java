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
package com.owl.adapter.metar;

import com.owl.core.api.*;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * METAR weather adapter implementation.
 * <p>
 * Polls METAR observations from NOAA at configurable intervals.
 * Demonstrates a simple HTTP polling adapter pattern.
 * <p>
 * Configuration (application.yml):
 * <pre>
 * owl:
 *   adapters:
 *     metar-http:
 *       enabled: true
 *       station-id: EBBR
 *       poll-interval-minutes: 30
 *       api-url: https://tgftp.nws.noaa.gov/data/observations/metar/stations/{STATION}.TXT
 * </pre>
 */
public class MetarAdapter implements WeatherAdapter {

    private static final String ADAPTER_NAME = "metar-http";

    // METAR parsing patterns
    private static final Pattern TEMP_PATTERN = Pattern.compile("\\s(M?\\d{2})/(M?\\d{2})\\s");
    private static final Pattern WIND_PATTERN = Pattern.compile("\\s(\\d{3})(\\d{2,3})(G\\d{2,3})?KT\\s");
    private static final Pattern PRESSURE_PATTERN = Pattern.compile("\\s[AQ](\\d{4})\\s");
    private static final Pattern VISIBILITY_PATTERN = Pattern.compile("\\s(\\d+)SM\\s");

    private Logger logger;
    private AdapterContext context;
    private MessageBus messageBus;
    private MetricsRegistry metrics;
    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    private String stationId;
    private String apiUrl;
    private int pollIntervalMinutes;

    private volatile Instant lastSuccessfulRead;
    private volatile String lastMetarRaw;
    private volatile boolean running = false;

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }

    @Override
    public String getDisplayName() {
        return "METAR Weather Adapter";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<EntityDefinition> getProvidedEntities() {
        return List.of(
                EntityDefinition.builder()
                        .entityId("sensor.metar_temperature")
                        .friendlyName("METAR Temperature")
                        .source(ADAPTER_NAME)
                        .unit("°C")
                        .deviceClass("temperature")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.metar_dewpoint")
                        .friendlyName("METAR Dewpoint")
                        .source(ADAPTER_NAME)
                        .unit("°C")
                        .deviceClass("temperature")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.metar_pressure")
                        .friendlyName("METAR Pressure")
                        .source(ADAPTER_NAME)
                        .unit("hPa")
                        .deviceClass("pressure")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.metar_wind_speed")
                        .friendlyName("METAR Wind Speed")
                        .source(ADAPTER_NAME)
                        .unit("kt")
                        .deviceClass("wind_speed")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.metar_wind_direction")
                        .friendlyName("METAR Wind Direction")
                        .source(ADAPTER_NAME)
                        .unit("°")
                        .deviceClass("wind_direction")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.metar_wind_gust")
                        .friendlyName("METAR Wind Gust")
                        .source(ADAPTER_NAME)
                        .unit("kt")
                        .deviceClass("wind_speed")
                        .aggregation(AggregationMethod.MAX)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.metar_visibility")
                        .friendlyName("METAR Visibility")
                        .source(ADAPTER_NAME)
                        .unit("SM")
                        .deviceClass("distance")
                        .aggregation(AggregationMethod.MEAN)
                        .build()
        );
    }

    @Override
    public void start(AdapterContext context) throws AdapterException {
        this.context = context;
        this.logger = context.getLogger();
        this.messageBus = context.getMessageBus();
        this.metrics = context.getMetrics();

        logger.info("Starting METAR adapter");

        // Load configuration
        AdapterConfiguration config = context.getConfiguration();
        this.stationId = config.getRequiredString("station-id");
        this.apiUrl = config.getString("api-url")
                .orElse("https://tgftp.nws.noaa.gov/data/observations/metar/stations/{STATION}.TXT");
        this.pollIntervalMinutes = config.getInt("poll-interval-minutes").orElse(30);

        // Validate configuration
        if (stationId.isEmpty()) {
            throw new AdapterException("Station ID is required");
        }

        // Initialize HTTP client
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Start scheduled polling
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "metar-poller");
            t.setDaemon(true);
            return t;
        });

        this.running = true;

        // Initial fetch immediately
        scheduler.execute(this::pollMetar);

        // Then schedule at fixed intervals
        scheduler.scheduleAtFixedRate(
                this::pollMetar,
                pollIntervalMinutes,
                pollIntervalMinutes,
                TimeUnit.MINUTES
        );

        logger.info("METAR adapter started: station={}, interval={} minutes",
                stationId, pollIntervalMinutes);
    }

    @Override
    public void stop() throws AdapterException {
        logger.info("Stopping METAR adapter");

        running = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("METAR adapter stopped");
    }

    @Override
    public AdapterHealth getHealth() {
        if (!running) {
            return AdapterHealth.unhealthy("Adapter not running");
        }

        if (lastSuccessfulRead == null) {
            return AdapterHealth.degraded("No successful reads yet", Map.of(
                    "station", stationId
            ));
        }

        Duration timeSinceLastRead = Duration.between(lastSuccessfulRead, Instant.now());
        long expectedInterval = pollIntervalMinutes * 60L;

        if (timeSinceLastRead.getSeconds() > expectedInterval * 3) {
            return AdapterHealth.unhealthy(
                    String.format("No data for %d minutes", timeSinceLastRead.toMinutes())
            );
        }

        if (timeSinceLastRead.getSeconds() > expectedInterval * 2) {
            return AdapterHealth.degraded(
                    "Data is stale",
                    Map.of(
                            "last_read", lastSuccessfulRead.toString(),
                            "minutes_ago", timeSinceLastRead.toMinutes()
                    )
            );
        }

        return AdapterHealth.healthy(
                String.format("Last update: %d minutes ago", timeSinceLastRead.toMinutes()),
                lastSuccessfulRead
        );
    }

    /**
     * Poll METAR data from NOAA.
     */
    private void pollMetar() {
        if (!running) {
            return;
        }

        try {
            logger.debug("Polling METAR for station {}", stationId);

            String url = apiUrl.replace("{STATION}", stationId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                logger.warn("METAR API returned status {}", response.statusCode());
                metrics.incrementCounter("errors");
                return;
            }

            String body = response.body();
            String[] lines = body.split("\n");

            if (lines.length < 2) {
                logger.warn("METAR response has insufficient lines");
                return;
            }

            // Second line contains the METAR observation
            String metar = lines[1].trim();
            this.lastMetarRaw = metar;

            logger.debug("Received METAR: {}", metar);

            // Parse and publish
            parseAndPublish(metar);

            this.lastSuccessfulRead = Instant.now();
            metrics.incrementCounter("success");

        } catch (Exception e) {
            logger.error("Error polling METAR", e);
            metrics.incrementCounter("errors");
        }
    }

    /**
     * Parse METAR string and publish sensor readings.
     */
    private void parseAndPublish(String metar) throws MessageBusException {
        Instant timestamp = Instant.now();

        // Add spaces around METAR for easier pattern matching
        String metarPadded = " " + metar + " ";

        // Parse temperature and dewpoint (e.g., "12/08" or "M02/M05")
        Matcher tempMatcher = TEMP_PATTERN.matcher(metarPadded);
        if (tempMatcher.find()) {
            double temp = parseTemp(tempMatcher.group(1));
            double dewpoint = parseTemp(tempMatcher.group(2));

            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.metar_temperature",
                    temp
            ));

            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.metar_dewpoint",
                    dewpoint
            ));
        }

        // Parse wind (e.g., "27015KT" or "27015G25KT")
        Matcher windMatcher = WIND_PATTERN.matcher(metarPadded);
        if (windMatcher.find()) {
            double direction = Double.parseDouble(windMatcher.group(1));
            double speed = Double.parseDouble(windMatcher.group(2));

            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.metar_wind_direction",
                    direction
            ));

            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.metar_wind_speed",
                    speed
            ));

            // Check for gust
            String gustStr = windMatcher.group(3);
            if (gustStr != null) {
                double gust = Double.parseDouble(gustStr.substring(1)); // Remove 'G'
                messageBus.publish(new SensorReading(
                        timestamp,
                        ADAPTER_NAME,
                        "sensor.metar_wind_gust",
                        gust
                ));
            }
        }

        // Parse pressure (e.g., "A3012" for inHg or "Q1013" for hPa)
        Matcher pressureMatcher = PRESSURE_PATTERN.matcher(metarPadded);
        if (pressureMatcher.find()) {
            String prefix = metarPadded.charAt(pressureMatcher.start() + 1) == 'A' ? "A" : "Q";
            String pressureStr = pressureMatcher.group(1);
            double pressureHpa;

            if (prefix.equals("A")) {
                // Convert inHg to hPa
                double pressureInHg = Double.parseDouble(pressureStr) / 100.0;
                pressureHpa = pressureInHg * 33.8639;
            } else {
                // Already in hPa
                pressureHpa = Double.parseDouble(pressureStr);
            }

            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.metar_pressure",
                    pressureHpa
            ));
        }

        // Parse visibility (e.g., "10SM")
        Matcher visibilityMatcher = VISIBILITY_PATTERN.matcher(metarPadded);
        if (visibilityMatcher.find()) {
            double visibility = Double.parseDouble(visibilityMatcher.group(1));
            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.metar_visibility",
                    visibility
            ));
        }
    }

    /**
     * Parse temperature string (handles negative with "M" prefix).
     */
    private double parseTemp(String tempStr) {
        if (tempStr.startsWith("M")) {
            return -Double.parseDouble(tempStr.substring(1));
        }
        return Double.parseDouble(tempStr);
    }
}
