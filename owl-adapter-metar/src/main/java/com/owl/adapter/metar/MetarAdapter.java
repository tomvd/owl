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
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
@Singleton
@Requires(property = "owl.adapters.metar-http.enabled", value = "true")
public class MetarAdapter implements WeatherAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MetarAdapter.class);
    private static final String ADAPTER_NAME = "metar-http";

    private static final Pattern TEMP_PATTERN = Pattern.compile("\\s(M?\\d{2})/(M?\\d{2})\\s");
    private static final Pattern WIND_PATTERN = Pattern.compile("\\s(\\d{3})(\\d{2,3})(G\\d{2,3})?KT\\s");
    private static final Pattern PRESSURE_PATTERN = Pattern.compile("\\s[AQ](\\d{4})\\s");
    private static final Pattern VISIBILITY_PATTERN = Pattern.compile("\\s(\\d+)SM\\s");

    private final MessageBus messageBus;
    private final MetricsRegistry metrics;
    private final MetarConfiguration config;

    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    private volatile Instant lastSuccessfulRead;
    private volatile String lastMetarRaw;
    private volatile boolean running = false;

    public MetarAdapter(MessageBus messageBus, MetricsRegistry metrics, MetarConfiguration config) {
        this.messageBus = messageBus;
        this.metrics = metrics;
        this.config = config;
    }

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

    @PostConstruct
    void start() {
        LOG.info("Starting METAR adapter");

        String stationId = config.getStationId();
        int pollIntervalMinutes = config.getPollIntervalMinutes();

        if (stationId == null || stationId.isEmpty()) {
            throw new RuntimeException("Station ID is required");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "metar-poller");
            t.setDaemon(true);
            return t;
        });

        this.running = true;

        scheduler.execute(this::pollMetar);
        scheduler.scheduleAtFixedRate(
                this::pollMetar,
                pollIntervalMinutes,
                pollIntervalMinutes,
                TimeUnit.MINUTES
        );

        LOG.info("METAR adapter started: station={}, interval={} minutes",
                stationId, pollIntervalMinutes);
    }

    @PreDestroy
    void stop() {
        LOG.info("Stopping METAR adapter");

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

        LOG.info("METAR adapter stopped");
    }

    @Override
    public AdapterHealth getHealth() {
        if (!running) {
            return AdapterHealth.unhealthy("Adapter not running");
        }

        if (lastSuccessfulRead == null) {
            return AdapterHealth.degraded("No successful reads yet", Map.of(
                    "station", config.getStationId()
            ));
        }

        Duration timeSinceLastRead = Duration.between(lastSuccessfulRead, Instant.now());
        long expectedInterval = config.getPollIntervalMinutes() * 60L;

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

    private void pollMetar() {
        if (!running) {
            return;
        }

        try {
            String stationId = config.getStationId();
            LOG.debug("Polling METAR for station {}", stationId);

            String url = config.getApiUrl().replace("{STATION}", stationId);
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
                LOG.warn("METAR API returned status {}", response.statusCode());
                metrics.incrementCounter("errors");
                return;
            }

            String body = response.body();
            String[] lines = body.split("\n");

            if (lines.length < 2) {
                LOG.warn("METAR response has insufficient lines");
                return;
            }

            String metar = lines[1].trim();
            this.lastMetarRaw = metar;

            LOG.debug("Received METAR: {}", metar);

            parseAndPublish(metar);

            this.lastSuccessfulRead = Instant.now();
            metrics.incrementCounter("success");

        } catch (Exception e) {
            LOG.error("Error polling METAR", e);
            metrics.incrementCounter("errors");
        }
    }

    private void parseAndPublish(String metar) throws MessageBusException {
        Instant timestamp = Instant.now();

        String metarPadded = " " + metar + " ";

        Matcher tempMatcher = TEMP_PATTERN.matcher(metarPadded);
        if (tempMatcher.find()) {
            double temp = parseTemp(tempMatcher.group(1));
            double dewpoint = parseTemp(tempMatcher.group(2));

            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.metar_temperature", temp));
            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.metar_dewpoint", dewpoint));
        }

        Matcher windMatcher = WIND_PATTERN.matcher(metarPadded);
        if (windMatcher.find()) {
            double direction = Double.parseDouble(windMatcher.group(1));
            double speed = Double.parseDouble(windMatcher.group(2));

            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.metar_wind_direction", direction));
            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.metar_wind_speed", speed));

            String gustStr = windMatcher.group(3);
            if (gustStr != null) {
                double gust = Double.parseDouble(gustStr.substring(1));
                messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.metar_wind_gust", gust));
            }
        }

        Matcher pressureMatcher = PRESSURE_PATTERN.matcher(metarPadded);
        if (pressureMatcher.find()) {
            String prefix = metarPadded.charAt(pressureMatcher.start() + 1) == 'A' ? "A" : "Q";
            String pressureStr = pressureMatcher.group(1);
            double pressureHpa;

            if (prefix.equals("A")) {
                double pressureInHg = Double.parseDouble(pressureStr) / 100.0;
                pressureHpa = pressureInHg * 33.8639;
            } else {
                pressureHpa = Double.parseDouble(pressureStr);
            }

            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.metar_pressure", pressureHpa));
        }

        Matcher visibilityMatcher = VISIBILITY_PATTERN.matcher(metarPadded);
        if (visibilityMatcher.find()) {
            double visibility = Double.parseDouble(visibilityMatcher.group(1));
            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.metar_visibility", visibility));
        }
    }

    private double parseTemp(String tempStr) {
        if (tempStr.startsWith("M")) {
            return -Double.parseDouble(tempStr.substring(1));
        }
        return Double.parseDouble(tempStr);
    }
}
