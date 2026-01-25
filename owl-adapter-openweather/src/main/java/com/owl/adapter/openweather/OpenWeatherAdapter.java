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
 * OpenWeather API adapter implementation.
 * <p>
 * Polls current weather data from OpenWeatherMap API at configurable intervals.
 * <p>
 * Configuration (application.yml):
 * <pre>
 * owl:
 *   adapters:
 *     openweather:
 *       enabled: true
 *       latitude: 51.1333
 *       longitude: 4.5667
 *       units: metric
 *       lang: en
 *       poll-interval-minutes: 10
 *       api-url: https://api.openweathermap.org/data/2.5/weather
 * </pre>
 * <p>
 * Note: The API key (appid) must be provided via environment variable OPENWEATHER_API_KEY
 * and NOT in the configuration file, as it is a secret.
 */
public class OpenWeatherAdapter implements WeatherAdapter {

    private static final String ADAPTER_NAME = "openweather";
    private static final String ENV_API_KEY = "OPENWEATHER_API_KEY";
    private static final String DEFAULT_API_URL = "https://api.openweathermap.org/data/2.5/weather";

    // Simple JSON extraction patterns (avoiding external JSON library dependency)
    private static final Pattern WIND_DEG_PATTERN = Pattern.compile("\"deg\"\\s*:\\s*(\\d+)");
    private static final Pattern CLOUDS_PATTERN = Pattern.compile("\"clouds\"\\s*:\\s*\\{[^}]*\"all\"\\s*:\\s*(\\d+)");
    private static final Pattern WEATHER_ID_PATTERN = Pattern.compile("\"weather\"\\s*:\\s*\\[\\s*\\{[^}]*\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern WEATHER_ICON_PATTERN = Pattern.compile("\"weather\"\\s*:\\s*\\[\\s*\\{[^}]*\"icon\"\\s*:\\s*\"([^\"]+)\"");

    private Logger logger;
    private AdapterContext context;
    private MessageBus messageBus;
    private MetricsRegistry metrics;
    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    private double latitude;
    private double longitude;
    private String units;
    private String lang;
    private String apiKey;
    private String apiUrl;
    private int pollIntervalMinutes;

    private volatile Instant lastSuccessfulRead;
    private volatile boolean running = false;

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }

    @Override
    public String getDisplayName() {
        return "OpenWeather API Adapter";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<EntityDefinition> getProvidedEntities() {
        return List.of(
                EntityDefinition.builder()
                        .entityId("sensor.openweather_wind_dir")
                        .friendlyName("OpenWeather Wind Direction")
                        .source(ADAPTER_NAME)
                        .unit("°")
                        .deviceClass(null)
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.openweather_clouds")
                        .friendlyName("OpenWeather Cloud Coverage")
                        .source(ADAPTER_NAME)
                        .unit("%")
                        .deviceClass(null)
                        .aggregation(AggregationMethod.MEAN)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.openweather_weather_id")
                        .friendlyName("OpenWeather Condition ID")
                        .source(ADAPTER_NAME)
                        .unit(null)
                        .deviceClass(null)
                        .aggregation(AggregationMethod.LAST)
                        .build(),

                EntityDefinition.builder()
                        .entityId("sensor.openweather_weather_icon")
                        .friendlyName("OpenWeather Icon")
                        .source(ADAPTER_NAME)
                        .unit(null)
                        .deviceClass(null)
                        .aggregation(AggregationMethod.LAST)
                        .build()
        );
    }

    @Override
    public void start(AdapterContext context) throws AdapterException {
        this.context = context;
        this.logger = context.getLogger();
        this.messageBus = context.getMessageBus();
        this.metrics = context.getMetrics();

        logger.info("Starting OpenWeather adapter");

        // Load configuration
        AdapterConfiguration config = context.getConfiguration();
        this.latitude = config.getDouble("latitude")
                .orElseThrow(() -> new AdapterException("latitude is required"));
        this.longitude = config.getDouble("longitude")
                .orElseThrow(() -> new AdapterException("longitude is required"));
        this.units = config.getString("units").orElse("metric");
        this.lang = config.getString("lang").orElse("en");
        this.apiUrl = config.getString("api-url").orElse(DEFAULT_API_URL);
        this.pollIntervalMinutes = config.getInt("poll-interval-minutes").orElse(10);

        // API key from environment variable (NOT from config file for security)
        this.apiKey = System.getenv(ENV_API_KEY);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new AdapterException(
                    "OpenWeather API key not found. Set the " + ENV_API_KEY + " environment variable.");
        }

        // Validate configuration
        if (latitude < -90 || latitude > 90) {
            throw new AdapterException("Invalid latitude: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new AdapterException("Invalid longitude: " + longitude);
        }

        // Initialize HTTP client
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Start scheduled polling
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "openweather-poller");
            t.setDaemon(true);
            return t;
        });

        this.running = true;

        // Initial fetch immediately
        scheduler.execute(this::pollWeather);

        // Then schedule at fixed intervals
        scheduler.scheduleAtFixedRate(
                this::pollWeather,
                pollIntervalMinutes,
                pollIntervalMinutes,
                TimeUnit.MINUTES
        );

        logger.info("OpenWeather adapter started: lat={}, lon={}, interval={} minutes",
                latitude, longitude, pollIntervalMinutes);
    }

    @Override
    public void stop() throws AdapterException {
        logger.info("Stopping OpenWeather adapter");

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

        logger.info("OpenWeather adapter stopped");
    }

    @Override
    public AdapterHealth getHealth() {
        if (!running) {
            return AdapterHealth.unhealthy("Adapter not running");
        }

        if (lastSuccessfulRead == null) {
            return AdapterHealth.degraded("No successful reads yet", Map.of(
                    "latitude", latitude,
                    "longitude", longitude
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
     * Poll weather data from OpenWeatherMap API.
     */
    private void pollWeather() {
        if (!running) {
            return;
        }

        try {
            logger.debug("Polling OpenWeather for lat={}, lon={}", latitude, longitude);

            String url = String.format("%s?lat=%f&lon=%f&units=%s&lang=%s&appid=%s",
                    apiUrl, latitude, longitude, units, lang, apiKey);

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
                logger.warn("OpenWeather API returned status {}: {}",
                        response.statusCode(), response.body());
                metrics.incrementCounter("errors");
                return;
            }

            String body = response.body();
            logger.debug("Received OpenWeather response: {}", body);

            // Parse and publish
            parseAndPublish(body);

            this.lastSuccessfulRead = Instant.now();
            metrics.incrementCounter("success");

        } catch (Exception e) {
            logger.error("Error polling OpenWeather", e);
            metrics.incrementCounter("errors");
        }
    }

    /**
     * Parse JSON response and publish sensor readings.
     */
    private void parseAndPublish(String json) throws MessageBusException {
        Instant timestamp = Instant.now();

        // Parse wind direction
        Matcher windDegMatcher = WIND_DEG_PATTERN.matcher(json);
        if (windDegMatcher.find()) {
            double windDir = Double.parseDouble(windDegMatcher.group(1));
            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.openweather_wind_dir",
                    windDir
            ));
        }

        // Parse cloud coverage
        Matcher cloudsMatcher = CLOUDS_PATTERN.matcher(json);
        if (cloudsMatcher.find()) {
            double clouds = Double.parseDouble(cloudsMatcher.group(1));
            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.openweather_clouds",
                    clouds
            ));
        }

        // Parse weather condition ID
        Matcher weatherIdMatcher = WEATHER_ID_PATTERN.matcher(json);
        if (weatherIdMatcher.find()) {
            double weatherId = Double.parseDouble(weatherIdMatcher.group(1));
            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.openweather_weather_id",
                    weatherId
            ));
        }

        // Parse weather icon
        // Icon format is like "04d" or "10n" - we store numeric part as value,
        // full icon string as attribute for display purposes
        Matcher weatherIconMatcher = WEATHER_ICON_PATTERN.matcher(json);
        if (weatherIconMatcher.find()) {
            String iconStr = weatherIconMatcher.group(1);
            // Extract numeric portion (e.g., "04d" -> 4, "10n" -> 10)
            String numericPart = iconStr.replaceAll("[^0-9]", "");
            double iconNumeric = numericPart.isEmpty() ? 0 : Double.parseDouble(numericPart);

            messageBus.publish(new SensorReading(
                    timestamp,
                    ADAPTER_NAME,
                    "sensor.openweather_weather_icon",
                    iconNumeric,
                    Map.of("icon", iconStr)
            ));
        }
    }
}
