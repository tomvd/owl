# Owl (OpenWeatherLink) - Adapter Developer Guide

**Version:** 2.0
**Last Updated:** February 2026
**License:** Apache 2.0

---

## Overview

Adapters are Micronaut beans that connect to weather data sources, parse data into
`SensorReading` events, and publish them to the `MessageBus`. The framework handles
entity registration, lifecycle management, and configuration binding automatically.

---

## Creating a New Adapter

### 1. Create a Gradle Module

Create `owl-adapter-{name}/build.gradle.kts`:

```kotlin
plugins {
    id("io.micronaut.library")
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-inject-java")
    implementation(project(":owl-core"))
}

micronaut {
    processing {
        annotations("com.owl.adapter.{name}.*")
    }
}
```

Add to `settings.gradle`:
```groovy
include 'owl-adapter-{name}'
```

Add as `runtimeOnly` dependency in `owl-core/build.gradle.kts`:
```kotlin
runtimeOnly(project(":owl-adapter-{name}"))
```

### 2. Create a Configuration Class

```java
@ConfigurationProperties("owl.adapters.{name}")
public class MyAdapterConfiguration {
    private String apiUrl = "https://api.example.com";
    private int pollIntervalMinutes = 10;

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String v) { this.apiUrl = v; }

    public int getPollIntervalMinutes() { return pollIntervalMinutes; }
    public void setPollIntervalMinutes(int v) { this.pollIntervalMinutes = v; }
}
```

Add defaults to `owl-core/src/main/resources/application.yml`:
```yaml
owl:
  adapters:
    my-adapter:
      enabled: false
      api-url: https://api.example.com
      poll-interval-minutes: 10
```

### 3. Create the Adapter Class

```java
@Singleton
@Requires(property = "owl.adapters.{name}.enabled", value = "true")
public class MyAdapter implements WeatherAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MyAdapter.class);
    private static final String ADAPTER_NAME = "{name}";

    private final MessageBus messageBus;
    private final MetricsRegistry metrics;
    private final MyAdapterConfiguration config;

    public MyAdapter(MessageBus messageBus, MetricsRegistry metrics,
                     MyAdapterConfiguration config) {
        this.messageBus = messageBus;
        this.metrics = metrics;
        this.config = config;
    }

    @Override public String getName() { return ADAPTER_NAME; }
    @Override public String getDisplayName() { return "My Adapter"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public List<EntityDefinition> getProvidedEntities() {
        return List.of(
            EntityDefinition.builder()
                .entityId("sensor.my_temperature")
                .friendlyName("My Temperature")
                .source(ADAPTER_NAME)
                .unit("°C")
                .deviceClass("temperature")
                .aggregation(AggregationMethod.MEAN)
                .build()
        );
    }

    @PostConstruct
    void start() {
        LOG.info("Starting adapter: {}", config.getApiUrl());
        // Initialize connections, start polling, etc.
    }

    @PreDestroy
    void stop() {
        LOG.info("Stopping adapter");
        // Clean up resources, stop threads
    }

    @Override
    public AdapterHealth getHealth() {
        return AdapterHealth.healthy("OK");
    }
}
```

---

## Key Concepts

- **`@Singleton`** — one adapter instance per application
- **`@Requires(property = "...", value = "true")`** — adapter only created when enabled
- **`@PostConstruct`** — called after construction, replaces old `start(AdapterContext)`
- **`@PreDestroy`** — called on shutdown, replaces old `stop()`
- **Constructor injection** — `MessageBus`, `MetricsRegistry`, config class injected automatically
- **Entity registration** — `AdapterLifecycleManager` calls `getProvidedEntities()` on all adapters at startup

### Publishing Events

```java
// Single event
messageBus.publish(new SensorReading(
    Instant.now(), ADAPTER_NAME, "sensor.my_temperature", 21.5
));

// Batch (more efficient)
messageBus.publishBatch(readings);

// With attributes
messageBus.publish(new SensorReading(
    Instant.now(), ADAPTER_NAME, "sensor.my_icon", 4.0,
    Map.of("icon", "04d")
));
```

### Recovery Support

Override `supportsRecovery()` and `requestRecovery()` if your adapter can backfill data:

```java
@Override
public boolean supportsRecovery() { return true; }

@Override
public RecoveryHandle requestRecovery(Instant from, Instant to) {
    // Start async recovery and return a handle
}
```

---

## Health Checks

Implement health status based on data freshness:

```java
@Override
public AdapterHealth getHealth() {
    if (!running) {
        return AdapterHealth.unhealthy("Adapter not running");
    }
    if (lastSuccessfulRead == null) {
        return AdapterHealth.degraded("No data yet", Map.of());
    }
    Duration since = Duration.between(lastSuccessfulRead, Instant.now());
    long expected = config.getPollIntervalMinutes() * 60L;
    if (since.getSeconds() > expected * 3) {
        return AdapterHealth.unhealthy("No data for " + since.toMinutes() + " minutes");
    }
    return AdapterHealth.healthy("OK", lastSuccessfulRead);
}
```

---

## Best Practices

1. **Use static loggers** — `LoggerFactory.getLogger(MyAdapter.class)`
2. **Daemon threads** — set daemon=true on polling threads
3. **Graceful shutdown** — `scheduler.awaitTermination()` in `@PreDestroy`
4. **Validate config early** — throw from `@PostConstruct` if config is invalid
5. **Metrics** — increment counters for successes and errors
6. **Log levels** — DEBUG for routine, INFO for lifecycle, WARN/ERROR for problems
