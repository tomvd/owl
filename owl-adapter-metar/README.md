# METAR Weather Adapter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

An open-source adapter for the Owl (OpenWeatherLink) weather data platform that retrieves METAR weather observations from NOAA.

## Overview

This adapter polls METAR (Meteorological Aerodrome Report) data from the NOAA aviation weather service at configurable intervals. METAR provides standardized weather observations from airports worldwide.

## Features

- Polls METAR observations at configurable intervals
- Parses standard METAR format
- Extracts: temperature, dewpoint, pressure, wind speed, wind direction, wind gusts, visibility
- Health monitoring and metrics
- Automatic retry on transient errors

## Installation

### As a Plugin

1. Build the standalone JAR:
   ```bash
   ./gradlew :owl-adapter-metar:adapterJar
   ```

2. Copy to the plugins directory:
   ```bash
   cp owl-adapter-metar/build/libs/*-standalone.jar plugins/
   ```

### As a Dependency

Add to your `build.gradle`:
```gradle
dependencies {
    implementation 'com.owl:owl-adapter-metar:1.0.0'
}
```

## Configuration

Add to your `application.yml`:

```yaml
owl:
  adapters:
    metar-http:
      enabled: true
      station-id: EBBR              # ICAO airport code
      poll-interval-minutes: 30     # How often to poll (default: 30)
      api-url: https://tgftp.nws.noaa.gov/data/observations/metar/stations/{STATION}.TXT
```

### Configuration Options

| Option | Required | Default | Description |
|--------|----------|---------|-------------|
| `enabled` | No | `false` | Enable/disable the adapter |
| `station-id` | Yes | - | ICAO airport code (e.g., EBBR, KJFK) |
| `poll-interval-minutes` | No | `30` | Polling interval in minutes |
| `api-url` | No | NOAA URL | Custom API URL (use `{STATION}` as placeholder) |

## Entities Provided

| Entity ID | Description | Unit | Aggregation |
|-----------|-------------|------|-------------|
| `sensor.metar_temperature` | Air temperature | °C | mean |
| `sensor.metar_dewpoint` | Dewpoint temperature | °C | mean |
| `sensor.metar_pressure` | Atmospheric pressure | hPa | mean |
| `sensor.metar_wind_speed` | Wind speed | kt | mean |
| `sensor.metar_wind_direction` | Wind direction | ° | mean |
| `sensor.metar_wind_gust` | Wind gust speed | kt | max |
| `sensor.metar_visibility` | Visibility | SM | mean |

## Finding Your Station

Find ICAO codes for airports at:
- [SkyVector](https://skyvector.com/)
- [AirNav](https://www.airnav.com/airports/)

Common European airports:
- EBBR - Brussels Airport
- EGLL - London Heathrow
- LFPG - Paris Charles de Gaulle
- EDDF - Frankfurt

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.
