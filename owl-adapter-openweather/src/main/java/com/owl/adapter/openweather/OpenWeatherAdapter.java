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
 * OpenWeather API adapter implementation.
 * <p>
 * Polls current weather data from OpenWeatherMap API at configurable intervals.
 */
@Singleton
@Requires(property = "owl.adapters.openweather.enabled", value = "true")
public class OpenWeatherAdapter implements WeatherAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(OpenWeatherAdapter.class);
    private static final String ADAPTER_NAME = "openweather";
    private static final String ENV_API_KEY = "OPENWEATHER_API_KEY";

    private static final Pattern WIND_DEG_PATTERN = Pattern.compile("\"deg\"\\s*:\\s*(\\d+)");
    private static final Pattern CLOUDS_PATTERN = Pattern.compile("\"clouds\"\\s*:\\s*\\{[^}]*\"all\"\\s*:\\s*(\\d+)");
    private static final Pattern WEATHER_ID_PATTERN = Pattern.compile("\"weather\"\\s*:\\s*\\[\\s*\\{[^}]*\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern WEATHER_ICON_PATTERN = Pattern.compile("\"weather\"\\s*:\\s*\\[\\s*\\{[^}]*\"icon\"\\s*:\\s*\"([^\"]+)\"");

    private final MessageBus messageBus;
    private final MetricsRegistry metrics;
    private final OpenWeatherConfiguration config;

    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;
    private String apiKey;

    private volatile Instant lastSuccessfulRead;
    private volatile boolean running = false;

    public OpenWeatherAdapter(MessageBus messageBus, MetricsRegistry metrics, OpenWeatherConfiguration config) {
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

    @PostConstruct
    void start() {
        LOG.info("Starting OpenWeather adapter");

        double latitude = config.getLatitude();
        double longitude = config.getLongitude();
        int pollIntervalMinutes = config.getPollIntervalMinutes();

        this.apiKey = System.getenv(ENV_API_KEY);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException(
                    "OpenWeather API key not found. Set the " + ENV_API_KEY + " environment variable.");
        }

        if (latitude < -90 || latitude > 90) {
            throw new RuntimeException("Invalid latitude: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new RuntimeException("Invalid longitude: " + longitude);
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "openweather-poller");
            t.setDaemon(true);
            return t;
        });

        this.running = true;

        scheduler.execute(this::pollWeather);
        scheduler.scheduleAtFixedRate(
                this::pollWeather,
                pollIntervalMinutes,
                pollIntervalMinutes,
                TimeUnit.MINUTES
        );

        LOG.info("OpenWeather adapter started: lat={}, lon={}, interval={} minutes",
                latitude, longitude, pollIntervalMinutes);
    }

    @PreDestroy
    void stop() {
        LOG.info("Stopping OpenWeather adapter");

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

        LOG.info("OpenWeather adapter stopped");
    }

    @Override
    public AdapterHealth getHealth() {
        if (!running) {
            return AdapterHealth.unhealthy("Adapter not running");
        }

        if (lastSuccessfulRead == null) {
            return AdapterHealth.degraded("No successful reads yet", Map.of(
                    "latitude", config.getLatitude(),
                    "longitude", config.getLongitude()
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

    private void pollWeather() {
        if (!running) {
            return;
        }

        try {
            LOG.debug("Polling OpenWeather for lat={}, lon={}", config.getLatitude(), config.getLongitude());

            String url = String.format("%s?lat=%f&lon=%f&units=%s&lang=%s&appid=%s",
                    config.getApiUrl(), config.getLatitude(), config.getLongitude(),
                    config.getUnits(), config.getLang(), apiKey);

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
                LOG.warn("OpenWeather API returned status {}: {}",
                        response.statusCode(), response.body());
                metrics.incrementCounter("errors");
                return;
            }

            String body = response.body();
            LOG.debug("Received OpenWeather response: {}", body);

            parseAndPublish(body);

            this.lastSuccessfulRead = Instant.now();
            metrics.incrementCounter("success");

        } catch (Exception e) {
            LOG.error("Error polling OpenWeather", e);
            metrics.incrementCounter("errors");
        }
    }

    private void parseAndPublish(String json) throws MessageBusException {
        Instant timestamp = Instant.now();

        Matcher windDegMatcher = WIND_DEG_PATTERN.matcher(json);
        if (windDegMatcher.find()) {
            double windDir = Double.parseDouble(windDegMatcher.group(1));
            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.openweather_wind_dir", windDir));
        }

        Matcher cloudsMatcher = CLOUDS_PATTERN.matcher(json);
        if (cloudsMatcher.find()) {
            double clouds = Double.parseDouble(cloudsMatcher.group(1));
            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.openweather_clouds", clouds));
        }

        Matcher weatherIdMatcher = WEATHER_ID_PATTERN.matcher(json);
        if (weatherIdMatcher.find()) {
            double weatherId = Double.parseDouble(weatherIdMatcher.group(1));
            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.openweather_weather_id", weatherId));
        }

        Matcher weatherIconMatcher = WEATHER_ICON_PATTERN.matcher(json);
        if (weatherIconMatcher.find()) {
            String iconStr = weatherIconMatcher.group(1);
            String numericPart = iconStr.replaceAll("[^0-9]", "");
            double iconNumeric = numericPart.isEmpty() ? 0 : Double.parseDouble(numericPart);
            messageBus.publish(new SensorReading(timestamp, ADAPTER_NAME, "sensor.openweather_weather_icon",
                    iconNumeric, Map.of("icon", iconStr)));
        }
    }
}
