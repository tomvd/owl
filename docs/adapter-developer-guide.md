# Owl (OpenWeatherLink) - Adapter Development Guide

**Version:** 1.0  
**Last Updated:** January 2025  
**License:** Apache 2.0

---

## Table of Contents

1. [Introduction](#introduction)
2. [Quick Start](#quick-start)
3. [Architecture Overview](#architecture-overview)
4. [Core Concepts](#core-concepts)
5. [Creating Your First Adapter](#creating-your-first-adapter)
6. [Testing Your Adapter](#testing-your-adapter)
7. [Configuration](#configuration)
8. [Advanced Topics](#advanced-topics)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)
11. [API Reference](#api-reference)

---

## Introduction

The Owl (OpenWeatherLink) system provides a plugin architecture that allows developers to create adapters for new weather data sources without modifying the core system. This guide will walk you through creating, testing, and deploying your own weather adapter.

### What You'll Need

- Java 17 or later
- Gradle or Maven
- Basic understanding of weather data sources
- Familiarity with Java concurrency (for advanced adapters)

### What You Can Build

- **HTTP/REST API adapters** - Poll weather APIs on a schedule
- **WebSocket adapters** - Real-time streaming data sources
- **Serial/USB adapters** - Connect to weather stations via serial ports
- **File-based adapters** - Parse weather data from files or logs
- **Database adapters** - Import historical weather data

---

## Quick Start

### 1. Create a New Gradle Project

```gradle
// build.gradle
plugins {
    id 'java'
}

group = 'com.example.weather'
version = '1.0.0'

repositories {
    mavenCentral()
}

dependencies {
    // Owl Core API
    compileOnly 'com.owl:owl-core-api:1.0.0'
    
    // SLF4J for logging
    implementation 'org.slf4j:slf4j-api:2.0.9'
    
    // Testing
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testImplementation 'org.mockito:mockito-core:5.5.0'
}

test {
    useJUnitPlatform()
}
```

### 2. Implement the Adapter Interface

```java
package com.example.owl.adapter.myweather;

import com.owl.core.api.*;
import java.time.Instant;
import java.util.List;

public class MyWeatherAdapter implements WeatherAdapter {
    
    @Override
    public String getName() {
        return "myweather-http";
    }
    
    @Override
    public String getDisplayName() {
        return "My Weather API Adapter";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public List<EntityDefinition> getProvidedEntities() {
        return List.of(
            EntityDefinition.builder()
                .entityId("sensor.myweather_temperature")
                .friendlyName("My Weather Temperature")
                .source(getName())
                .unit("°C")
                .deviceClass("temperature")
                .aggregation(AggregationMethod.MEAN)
                .build()
        );
    }
    
    @Override
    public void start(AdapterContext context) throws AdapterException {
        // Initialize your adapter
        MessageBus bus = context.getMessageBus();
        
        // Publish a test reading
        bus.publish(new SensorReading(
            Instant.now(),
            getName(),
            "sensor.myweather_temperature",
            21.5
        ));
    }
    
    @Override
    public void stop() throws AdapterException {
        // Clean up resources
    }
    
    @Override
    public AdapterHealth getHealth() {
        return AdapterHealth.healthy("Operating normally");
    }
}
```

### 3. Create the Provider

```java
package com.example.owl.adapter.myweather;

import com.owl.core.api.*;

public class MyWeatherAdapterProvider implements AdapterProvider {
    
    @Override
    public WeatherAdapter createAdapter() {
        return new MyWeatherAdapter();
    }
    
    @Override
    public ProviderMetadata getMetadata() {
        return new ProviderMetadata(
            "myweather-http",
            "My Weather API Adapter",
            "1.0.0",
            "Your Name",
            "Connects to MyWeather API service"
        );
    }
}
```

### 4. Register Your Provider

Create file: `src/main/resources/META-INF/services/com.owl.core.api.AdapterProvider`

```
com.example.owl.adapter.myweather.MyWeatherAdapterProvider
```

### 5. Build and Deploy

```bash
./gradlew build

# Copy JAR to the owl plugins directory
cp build/libs/myweather-adapter-1.0.0.jar /opt/owl/plugins/
```

### 6. Configure

Add to `application.yml`:

```yaml
datasources:
  myweather-http:
    enabled: true
    api-key: your-api-key-here
    poll-interval-minutes: 10
```

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    YOUR ADAPTER JAR                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  MyWeatherAdapterProvider                            │   │
│  │  (registered via ServiceLoader)                      │   │
│  └────────────────┬────────────────────────────────────┘   │
│                   │ creates                                 │
│                   ▼                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  MyWeatherAdapter                                    │   │
│  │  • Connects to data source                           │   │
│  │  • Parses data                                       │   │
│  │  • Publishes events                                  │   │
│  └────────────────┬────────────────────────────────────┘   │
└───────────────────┼──────────────────────────────────────────┘
                    │ publishes
                    ▼
┌─────────────────────────────────────────────────────────────┐
│                  OWL CORE SYSTEM                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Message Bus                                         │   │
│  │  • Routes events to persistence                      │   │
│  │  • Triggers aggregation                              │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Entity Registry                                     │   │
│  │  • Stores entity definitions                         │   │
│  │  • Provides metadata for queries                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Persistence Layer                                   │   │
│  │  • Writes events to TimescaleDB                      │   │
│  │  • Computes statistics                               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Concepts

### Adapters

An **adapter** is responsible for:
- Connecting to a data source
- Reading/receiving data
- Parsing data into events
- Publishing events to the message bus
- Managing its lifecycle (start/stop)
- Reporting health status

### Entities

An **entity** represents a single measurable value (sensor reading). Examples:
- `sensor.davis_temp_out` - Outside temperature from Davis station
- `sensor.metar_pressure` - Atmospheric pressure from METAR
- `sensor.lightning_count` - Number of lightning strikes

Each entity has:
- **Unique ID** - Following pattern `sensor.<source>_<measurement>`
- **Friendly name** - Human-readable label
- **Unit of measurement** - e.g., °C, hPa, km/h
- **Device class** - Category (temperature, pressure, wind, etc.)
- **Aggregation method** - How to compute statistics (mean, max, min, sum, last)

### Events

**Events** are the data that flows through the system:

```java
// Simple sensor reading
SensorReading reading = new SensorReading(
    Instant.now(),              // When measurement was taken
    "myweather-http",           // Your adapter name
    "sensor.myweather_temp",    // Entity ID
    21.5                        // Value
);

// With optional attributes
SensorReading readingWithAttrs = new SensorReading(
    Instant.now(),
    "myweather-http",
    "sensor.myweather_temp",
    21.5,
    Map.of(
        "quality", "good",
        "source_type", "api"
    )
);
```

### Message Bus

The **message bus** is how your adapter communicates with the core system:

```java
MessageBus bus = context.getMessageBus();

// Publish single event
bus.publish(event);

// Publish multiple events efficiently
bus.publishBatch(List.of(event1, event2, event3));
```

---

## Creating Your First Adapter

Let's create a complete adapter for a fictional weather API.

### Step 1: Define Your Entities

First, decide what measurements your data source provides:

```java
@Override
public List<EntityDefinition> getProvidedEntities() {
    return List.of(
        // Temperature
        EntityDefinition.builder()
            .entityId("sensor.myweather_temperature")
            .friendlyName("MyWeather Temperature")
            .source(getName())
            .unit("°C")
            .deviceClass("temperature")
            .stateClass("measurement")
            .aggregation(AggregationMethod.MEAN)  // Average over time
            .build(),
        
        // Humidity
        EntityDefinition.builder()
            .entityId("sensor.myweather_humidity")
            .friendlyName("MyWeather Humidity")
            .source(getName())
            .unit("%")
            .deviceClass("humidity")
            .aggregation(AggregationMethod.MEAN)
            .build(),
        
        // Wind speed
        EntityDefinition.builder()
            .entityId("sensor.myweather_wind_speed")
            .friendlyName("MyWeather Wind Speed")
            .source(getName())
            .unit("km/h")
            .deviceClass("wind_speed")
            .aggregation(AggregationMethod.MEAN)
            .build(),
        
        // Wind gust (maximum is more meaningful than average)
        EntityDefinition.builder()
            .entityId("sensor.myweather_wind_gust")
            .friendlyName("MyWeather Wind Gust")
            .source(getName())
            .unit("km/h")
            .deviceClass("wind_speed")
            .aggregation(AggregationMethod.MAX)  // Maximum over time
            .build()
    );
}
```

### Step 2: Implement Initialization

```java
private Logger logger;
private MessageBus messageBus;
private ScheduledExecutorService scheduler;
private HttpClient httpClient;
private String apiKey;
private int pollIntervalMinutes;
private volatile boolean running = false;

@Override
public void start(AdapterContext context) throws AdapterException {
    this.logger = context.getLogger();
    this.messageBus = context.getMessageBus();
    
    logger.info("Starting MyWeather adapter");
    
    // Load configuration
    AdapterConfiguration config = context.getConfiguration();
    this.apiKey = config.getRequiredString("api-key");
    this.pollIntervalMinutes = config.getInt("poll-interval-minutes").orElse(10);
    
    // Validate configuration
    if (apiKey.isEmpty()) {
        throw new AdapterException("API key is required");
    }
    
    // Initialize HTTP client
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    
    // Start scheduled polling
    this.scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "myweather-poller");
        t.setDaemon(true);  // Don't prevent JVM shutdown
        return t;
    });
    
    this.running = true;
    
    // Poll immediately on startup
    scheduler.execute(this::pollWeatherData);
    
    // Then poll at fixed intervals
    scheduler.scheduleAtFixedRate(
        this::pollWeatherData,
        pollIntervalMinutes,
        pollIntervalMinutes,
        TimeUnit.MINUTES
    );
    
    logger.info("MyWeather adapter started: interval={} minutes", pollIntervalMinutes);
}
```

### Step 3: Implement Data Polling

```java
private void pollWeatherData() {
    if (!running) {
        return;
    }
    
    try {
        logger.debug("Polling MyWeather API");
        
        // Build request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.myweather.com/current?key=" + apiKey))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        
        // Execute request
        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 200) {
            logger.warn("API returned status {}", response.statusCode());
            context.getMetrics().incrementCounter("myweather.errors");
            return;
        }
        
        // Parse JSON response
        String json = response.body();
        WeatherData data = parseJson(json);
        
        // Publish events
        publishEvents(data);
        
        context.getMetrics().incrementCounter("myweather.success");
        
    } catch (Exception e) {
        logger.error("Error polling MyWeather API", e);
        context.getMetrics().incrementCounter("myweather.errors");
    }
}

private void publishEvents(WeatherData data) throws MessageBusException {
    Instant timestamp = Instant.now();
    
    messageBus.publish(new SensorReading(
        timestamp,
        getName(),
        "sensor.myweather_temperature",
        data.temperature
    ));
    
    messageBus.publish(new SensorReading(
        timestamp,
        getName(),
        "sensor.myweather_humidity",
        data.humidity
    ));
    
    messageBus.publish(new SensorReading(
        timestamp,
        getName(),
        "sensor.myweather_wind_speed",
        data.windSpeed
    ));
    
    messageBus.publish(new SensorReading(
        timestamp,
        getName(),
        "sensor.myweather_wind_gust",
        data.windGust
    ));
}
```

### Step 4: Implement Shutdown

```java
@Override
public void stop() throws AdapterException {
    logger.info("Stopping MyWeather adapter");
    
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
    
    logger.info("MyWeather adapter stopped");
}
```

### Step 5: Implement Health Check

```java
private volatile Instant lastSuccessfulRead;

@Override
public AdapterHealth getHealth() {
    if (!running) {
        return AdapterHealth.unhealthy("Adapter not running");
    }
    
    if (lastSuccessfulRead == null) {
        return AdapterHealth.degraded("No successful reads yet", Map.of());
    }
    
    Duration timeSinceLastRead = Duration.between(lastSuccessfulRead, Instant.now());
    long expectedInterval = pollIntervalMinutes * 60L;
    
    // Unhealthy if no data for 3x the poll interval
    if (timeSinceLastRead.getSeconds() > expectedInterval * 3) {
        return AdapterHealth.unhealthy(
            String.format("No data for %d minutes", timeSinceLastRead.toMinutes())
        );
    }
    
    // Degraded if no data for 2x the poll interval
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
        String.format("Last update: %d minutes ago", timeSinceLastRead.toMinutes())
    );
}
```

---

## Testing Your Adapter

### Unit Testing

```java
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MyWeatherAdapterTest {
    
    @Test
    void testGetProvidedEntities() {
        MyWeatherAdapter adapter = new MyWeatherAdapter();
        List<EntityDefinition> entities = adapter.getProvidedEntities();
        
        assertEquals(4, entities.size());
        assertTrue(entities.stream()
            .anyMatch(e -> e.getEntityId().equals("sensor.myweather_temperature")));
    }
    
    @Test
    void testStartWithValidConfig() throws Exception {
        MyWeatherAdapter adapter = new MyWeatherAdapter();
        
        // Mock context
        AdapterContext context = mock(AdapterContext.class);
        MessageBus messageBus = mock(MessageBus.class);
        AdapterConfiguration config = mock(AdapterConfiguration.class);
        Logger logger = mock(Logger.class);
        
        when(context.getMessageBus()).thenReturn(messageBus);
        when(context.getConfiguration()).thenReturn(config);
        when(context.getLogger()).thenReturn(logger);
        when(config.getRequiredString("api-key")).thenReturn("test-key");
        when(config.getInt("poll-interval-minutes")).thenReturn(Optional.of(10));
        
        // Should not throw
        adapter.start(context);
        adapter.stop();
    }
    
    @Test
    void testStartWithMissingApiKey() {
        MyWeatherAdapter adapter = new MyWeatherAdapter();
        
        AdapterContext context = mock(AdapterContext.class);
        AdapterConfiguration config = mock(AdapterConfiguration.class);
        
        when(context.getConfiguration()).thenReturn(config);
        when(config.getRequiredString("api-key"))
            .thenThrow(new ConfigurationException("Required configuration missing: api-key"));
        
        assertThrows(ConfigurationException.class, () -> adapter.start(context));
    }
}
```

### Integration Testing

```java
@Test
void testFullDataFlow() throws Exception {
    MyWeatherAdapter adapter = new MyWeatherAdapter();
    
    // Create real context with in-memory message bus
    TestMessageBus messageBus = new TestMessageBus();
    AdapterContext context = createTestContext(messageBus);
    
    adapter.start(context);
    
    // Wait for initial poll
    Thread.sleep(2000);
    
    // Verify events were published
    List<WeatherEvent> events = messageBus.getPublishedEvents();
    assertTrue(events.size() >= 4, "Should have published at least 4 events");
    
    adapter.stop();
}
```

---

## Configuration

### Configuration Schema

Your adapter's configuration lives under `datasources.<adapter-name>` in `application.yml`:

```yaml
datasources:
  myweather-http:
    enabled: true              # Required: enable/disable adapter
    api-key: "abc123"          # Your custom config
    poll-interval-minutes: 10  # Your custom config
    location:
      lat: 51.13
      lon: 4.56
```

### Accessing Configuration

```java
AdapterConfiguration config = context.getConfiguration();

// Check if enabled (framework handles this, but you can check too)
if (!config.isEnabled()) {
    return;
}

// Get required values (throws ConfigurationException if missing)
String apiKey = config.getRequiredString("api-key");

// Get optional values with defaults
int pollInterval = config.getInt("poll-interval-minutes").orElse(10);
String endpoint = config.getString("endpoint").orElse("https://api.default.com");

// Get nested configuration
Double lat = config.getDouble("location.lat").orElse(0.0);
```

### Configuration Best Practices

1. **Always provide defaults** for optional values
2. **Validate early** in the `start()` method
3. **Document all config options** in your README
4. **Use meaningful names** (e.g., `poll-interval-minutes` not `interval`)
5. **Don't hardcode** API keys or URLs

---

## Advanced Topics

### Recovery/Backfill Support

If your data source supports retrieving historical data, implement recovery:

```java
@Override
public boolean supportsRecovery() {
    return true;
}

@Override
public RecoveryHandle requestRecovery(Instant fromTime, Instant toTime) 
        throws AdapterException {
    
    logger.info("Starting recovery from {} to {}", fromTime, toTime);
    
    RecoveryHandleImpl handle = new RecoveryHandleImpl();
    
    // Start recovery in background thread
    CompletableFuture.runAsync(() -> {
        try {
            handle.setState(RecoveryHandle.State.RUNNING);
            
            // Fetch historical data from API
            List<HistoricalReading> data = fetchHistoricalData(fromTime, toTime);
            
            // Publish events
            for (HistoricalReading reading : data) {
                publishHistoricalReading(reading);
                handle.incrementRecordsRecovered();
            }
            
            handle.setState(RecoveryHandle.State.COMPLETED);
            
        } catch (Exception e) {
            logger.error("Recovery failed", e);
            handle.setState(RecoveryHandle.State.FAILED);
        }
    });
    
    return handle;
}
```

### WebSocket Adapters

For real-time streaming data:

```java
public class MyStreamingAdapter implements WeatherAdapter {
    
    private WebSocketClient webSocketClient;
    
    @Override
    public void start(AdapterContext context) throws AdapterException {
        String wsUrl = config.getRequiredString("websocket-url");
        
        this.webSocketClient = new WebSocketClient(URI.create(wsUrl)) {
            @Override
            public void onMessage(String message) {
                handleWebSocketMessage(message);
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.warn("WebSocket closed: {}", reason);
                // Implement reconnection logic
            }
        };
        
        webSocketClient.connect();
    }
    
    private void handleWebSocketMessage(String message) {
        try {
            WeatherData data = parseJson(message);
            publishEvents(data);
        } catch (Exception e) {
            logger.error("Error processing message", e);
        }
    }
}
```

### High-Frequency Data

For high-frequency events (e.g., lightning strikes), consider pre-aggregation:

```java
public class LightningAdapter implements WeatherAdapter {
    
    private BlockingQueue<LightningStrike> strikeQueue;
    private ExecutorService aggregator;
    
    @Override
    public void start(AdapterContext context) {
        this.strikeQueue = new LinkedBlockingQueue<>(10000);
        
        // Start aggregation thread
        this.aggregator = Executors.newSingleThreadExecutor();
        aggregator.submit(() -> {
            while (running) {
                aggregateAndPublish();
            }
        });
        
        // Connect to WebSocket...
    }
    
    private void aggregateAndPublish() {
        // Collect strikes for 5 minutes
        List<LightningStrike> strikes = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 300_000; // 5 min
        
        while (System.currentTimeMillis() < deadline) {
            try {
                LightningStrike strike = strikeQueue.poll(1, TimeUnit.SECONDS);
                if (strike != null) {
                    strikes.add(strike);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        // Compute aggregates
        int count = strikes.size();
        double nearest = strikes.stream()
            .mapToDouble(LightningStrike::getDistance)
            .min()
            .orElse(999.9);
        
        // Publish aggregated values
        messageBus.publish(new SensorReading(
            Instant.now(),
            getName(),
            "sensor.lightning_count",
            count
        ));
        
        messageBus.publish(new SensorReading(
            Instant.now(),
            getName(),
            "sensor.lightning_nearest",
            nearest
        ));
    }
}
```

### Blocking I/O (Serial Ports)

For blocking operations like serial communication:

```java
public class SerialAdapter implements WeatherAdapter {
    
    private ExecutorService serialThread;
    private SerialPort serialPort;
    
    @Override
    public void start(AdapterContext context) {
        String portName = config.getRequiredString("serial-port");
        
        // Use dedicated thread for blocking serial I/O
        this.serialThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "serial-reader");
            t.setDaemon(true);
            return t;
        });
        
        serialThread.submit(() -> {
            try {
                serialPort = SerialPort.getCommPort(portName);
                serialPort.openPort();
                
                while (running) {
                    // This blocks until data available
                    byte[] buffer = new byte[1024];
                    int bytesRead = serialPort.readBytes(buffer, buffer.length);
                    
                    if (bytesRead > 0) {
                        processSerialData(buffer, bytesRead);
                    }
                }
            } catch (Exception e) {
                logger.error("Serial port error", e);
            } finally {
                if (serialPort != null) {
                    serialPort.closePort();
                }
            }
        });
    }
}
```

---

## Best Practices

### 1. Error Handling

**DO:**
- Log errors with context
- Increment error metrics
- Continue running after recoverable errors
- Implement retry logic with exponential backoff

**DON'T:**
- Crash the adapter on transient errors
- Throw exceptions from background threads
- Spam logs with repetitive errors

```java
private void pollWithRetry() {
    int attempt = 0;
    while (attempt < 3) {
        try {
            pollWeatherData();
            return; // Success
        } catch (IOException e) {
            attempt++;
            if (attempt >= 3) {
                logger.error("Failed after 3 attempts", e);
                context.getMetrics().incrementCounter("myweather.failures");
            } else {
                long backoff = (long) Math.pow(2, attempt) * 1000;
                logger.warn("Attempt {} failed, retrying in {}ms", attempt, backoff);
                Thread.sleep(backoff);
            }
        }
    }
}
```

### 2. Thread Safety

**DO:**
- Use thread-safe collections
- Mark mutable state as `volatile`
- Synchronize shared state access
- Use concurrent utilities (AtomicInteger, etc.)

**DON'T:**
- Share mutable state without synchronization
- Block the main thread in `start()`
- Forget to interrupt threads in `stop()`

### 3. Resource Management

**DO:**
- Close connections in `stop()`
- Use try-with-resources for auto-closeable resources
- Set threads as daemon threads
- Clean up on errors

**DON'T:**
- Leave connections open
- Forget to shutdown ExecutorServices
- Create memory leaks with thread locals

### 4. Logging

**DO:**
- Use appropriate log levels (debug, info, warn, error)
- Include relevant context in log messages
- Use parameterized logging (SLF4J style)

**DON'T:**
- Log sensitive data (API keys, passwords)
- Log at INFO level for routine operations
- Create new logger instances per message

```java
// Good
logger.debug("Polling API: url={}, interval={}s", apiUrl, pollInterval);

// Bad
logger.info("Polling " + apiUrl + " every " + pollInterval + " seconds");
```

### 5. Configuration

**DO:**
- Validate configuration in `start()`
- Provide sensible defaults
- Document all config options
- Use type-safe config access

**DON'T:**
- Silently ignore invalid config
- Use magic numbers
- Require unnecessary configuration

---

## Troubleshooting

### Adapter Not Starting

**Symptoms:** Adapter doesn't appear in logs or health checks

**Solutions:**
1. Check `META-INF/services/com.weather.core.api.AdapterProvider` exists and has correct content
2. Verify JAR is in the plugins directory
3. Check `enabled: true` in configuration
4. Look for startup exceptions in logs

### Events Not Being Persisted

**Symptoms:** Adapter runs but no data appears in database

**Solutions:**
1. Check entity IDs match exactly (case-sensitive)
2. Verify timestamps are valid (not in future, not too far in past)
3. Check message bus errors in logs
4. Ensure values are not NaN or Infinity

### Health Check Failing

**Symptoms:** Adapter shows as UNHEALTHY

**Solutions:**
1. Check data source is accessible
2. Verify credentials/API keys are valid
3. Check network connectivity
4. Review error logs for exceptions

### Memory Leaks

**Symptoms:** Memory usage grows over time

**Solutions:**
1. Check for undisposed resources (HTTP clients, connections)
2. Verify ExecutorServices are shutdown in `stop()`
3. Look for accumulating collections
4. Use memory profiler (VisualVM, JProfiler)

---

## API Reference

### Core Interfaces

See the included `plugin-api-interfaces.java` for complete API documentation.

Key interfaces:
- `WeatherAdapter` - Main adapter interface
- `AdapterProvider` - Service provider for discovery
- `AdapterContext` - Context provided to adapters
- `MessageBus` - Event publishing interface
- `EntityDefinition` - Entity metadata
- `SensorReading` - Standard event type

### Common Patterns

#### HTTP Polling Adapter

```java
public class HttpPollingAdapter implements WeatherAdapter {
    // See METAR adapter example in adapter-provider-implementation.java
}
```

#### WebSocket Streaming Adapter

```java
public class WebSocketAdapter implements WeatherAdapter {
    // Connect to WebSocket, handle messages asynchronously
}
```

#### Serial Port Adapter

```java
public class SerialAdapter implements WeatherAdapter {
    // Use dedicated thread for blocking serial I/O
}
```

---

## Example: Complete Adapter Template

```java
package com.example.owl.adapter.template;

import com.owl.core.api.*;
import org.slf4j.Logger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class TemplateAdapter implements WeatherAdapter {
    
    private Logger logger;
    private AdapterContext context;
    private MessageBus messageBus;
    private volatile boolean running = false;
    
    // TODO: Add your adapter-specific fields
    
    @Override
    public String getName() {
        return "template-adapter";
    }
    
    @Override
    public String getDisplayName() {
        return "Template Adapter";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public List<EntityDefinition> getProvidedEntities() {
        // TODO: Define your entities
        return List.of();
    }
    
    @Override
    public void start(AdapterContext context) throws AdapterException {
        this.context = context;
        this.logger = context.getLogger();
        this.messageBus = context.getMessageBus();
        
        logger.info("Starting {} adapter", getName());
        
        // TODO: Load configuration
        // TODO: Initialize connections
        // TODO: Start background tasks
        
        this.running = true;
        logger.info("{} adapter started", getName());
    }
    
    @Override
    public void stop() throws AdapterException {
        logger.info("Stopping {} adapter", getName());
        running = false;
        
        // TODO: Stop background tasks
        // TODO: Close connections
        // TODO: Clean up resources
        
        logger.info("{} adapter stopped", getName());
    }
    
    @Override
    public AdapterHealth getHealth() {
        // TODO: Implement health check logic
        return AdapterHealth.healthy("Operating normally");
    }
}

public class TemplateAdapterProvider implements AdapterProvider {
    
    @Override
    public WeatherAdapter createAdapter() {
        return new TemplateAdapter();
    }
    
    @Override
    public ProviderMetadata getMetadata() {
        return new ProviderMetadata(
            "template-adapter",
            "Template Adapter",
            "1.0.0",
            "Your Name",
            "Description of what this adapter does"
        );
    }
}
```

---

## Getting Help

- **GitHub Issues**: https://github.com/owl-project/owl-core/issues
- **Documentation**: https://docs.openweatherlink.dev
- **Discussions**: https://github.com/owl-project/owl-core/discussions

---

## License

This documentation is licensed under Apache 2.0.
