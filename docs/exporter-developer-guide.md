# Owl (OpenWeatherLink) - Exporter Developer Guide

**Version:** 1.0
**Last Updated:** February 2026
**License:** Apache 2.0

---

## Overview

Exporters are Micronaut beans that subscribe to `StatisticsComputedEvent` and generate
output files (JSON, CSV, etc.) to configured destinations. The export system is designed
to be extended with new exporter classes without modifying existing code.

---

## Architecture

```
StatisticsComputedEvent
    │
    ├──> CurrentExporter      → current.json
    ├──> TwentyFourHourExporter → 24h.json
    ├──> ArchiveExporter      → archive/{yyyy}/{MM}/{dd}.json
    └──> YourExporter         → your-output.json
```

Each exporter:
- Is a `@Singleton` bean with `@Requires(property = "owl.services.export.enabled")`
- Subscribes to `StatisticsComputedEvent` in `@PostConstruct`
- Uses `StatisticsAccess` to query data
- Writes to `List<ExportDestination>` (local files + optional FTP)
- Uses `JsonExporter` for JSON generation (or implements its own format)

---

## Creating a New Exporter

### 1. Create the Exporter Class

```java
@Singleton
@Requires(property = "owl.services.export.enabled", value = "true")
public class MyExporter {

    private static final Logger LOG = LoggerFactory.getLogger(MyExporter.class);

    private final MessageBus messageBus;
    private final StatisticsAccess statisticsAccess;
    private final EntityRegistry entityRegistry;
    private final List<ExportDestination> destinations;
    private final ExportConfiguration config;

    public MyExporter(
            MessageBus messageBus,
            StatisticsAccess statisticsAccess,
            EntityRegistry entityRegistry,
            List<ExportDestination> destinations,
            ExportConfiguration config) {
        this.messageBus = messageBus;
        this.statisticsAccess = statisticsAccess;
        this.entityRegistry = entityRegistry;
        this.destinations = destinations;
        this.config = config;
    }

    @PostConstruct
    void init() {
        messageBus.subscribe(StatisticsComputedEvent.class, this::onStatisticsComputed);
        LOG.info("MyExporter initialized");
    }

    private void onStatisticsComputed(StatisticsComputedEvent event) {
        try {
            Instant windowEnd = event.windowEnd();

            // Query data
            List<ShortTermRecord> records = statisticsAccess.getShortTermStatistics(
                    windowEnd.minusSeconds(300), windowEnd);

            // Generate output
            byte[] data = generateOutput(records);

            // Write to all destinations
            for (ExportDestination dest : destinations) {
                dest.write("my-export.json", data);
            }

        } catch (Exception e) {
            LOG.error("Failed to generate export", e);
        }
    }

    private byte[] generateOutput(List<ShortTermRecord> records) {
        // Build your output format
        return "{}".getBytes();
    }
}
```

### 2. Available Data

Use `StatisticsAccess` to query aggregated statistics:

```java
// All entities for a time range
List<ShortTermRecord> all = statisticsAccess.getShortTermStatistics(start, end);

// Specific entity
List<ShortTermRecord> temps = statisticsAccess.getShortTermStatistics(
    "sensor.davis_temp_out", start, end);
```

Each `ShortTermRecord` contains: `startTs`, `entityId`, `mean`, `min`, `max`, `last`, `sum`, `count`, `attributes`.

### 3. Configuration

Access export config via `ExportConfiguration`:

```java
String outputDir = config.getOutputDirectory();
Set<String> entities = config.getEntities24hSet();
```

Add custom config fields by extending `ExportConfiguration` or using
`@ConfigurationProperties` with a sub-key.

---

## Export Destinations

Destinations are produced by `ExportDestinationFactory`:
- **`LocalFileDestination`** — always active, writes to `output-directory`
- **`FtpDestination`** — active when `ftp-enabled: true`

To add a new destination type, add it to `ExportDestinationFactory`.
