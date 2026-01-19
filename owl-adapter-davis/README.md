# Davis Vantage Pro Adapter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

An open-source adapter for the Owl (OpenWeatherLink) weather data platform that connects to Davis Vantage Pro weather stations via serial port.

## Overview

This adapter communicates with Davis Vantage Pro and Vantage Pro2 weather consoles through a serial connection. It reads real-time LOOP data packets every 2.5 seconds and supports archive data recovery for gap filling after system downtime.

## Features

- Real-time LOOP packet reading (2.5-second intervals)
- Archive data recovery for gap filling
- Automatic reconnection on serial port errors
- Support for all Davis sensors
- Thread-isolated serial I/O (won't affect other adapters)

## Installation

### As a Plugin

1. Build the standalone JAR:
   ```bash
   ./gradlew :owl-adapter-davis:adapterJar
   ```

2. Copy to the plugins directory:
   ```bash
   cp owl-adapter-davis/build/libs/*-standalone.jar plugins/
   ```

### As a Dependency

Add to your `build.gradle`:
```gradle
dependencies {
    implementation 'com.owl:owl-adapter-davis:1.0.0'
}
```

## Configuration

Add to your `application.yml`:

```yaml
owl:
  adapters:
    davis-serial:
      enabled: true
      serial-port: COM4            # or /dev/ttyUSB0 on Linux
      baud-rate: 19200             # Davis standard baud rate
      latitude: 51.063             # Station latitude (for solar calculations)
      longitude: 4.667             # Station longitude
      altitude: 32.0               # Station altitude in meters
      loop-count: 200              # Number of LOOP packets per request
      wakeup-timeout-ms: 3000      # Console wakeup timeout
      reconnect-delay-ms: 5000     # Delay before reconnection attempt
```

### Configuration Options

| Option | Required | Default | Description |
|--------|----------|---------|-------------|
| `enabled` | No | `false` | Enable/disable the adapter |
| `serial-port` | Yes | - | Serial port name (COM4, /dev/ttyUSB0) |
| `baud-rate` | No | `19200` | Serial baud rate |
| `latitude` | No | `0.0` | Station latitude for solar calculations |
| `longitude` | No | `0.0` | Station longitude |
| `altitude` | No | `0.0` | Station altitude in meters |
| `loop-count` | No | `200` | LOOP packets per request |
| `wakeup-timeout-ms` | No | `3000` | Console wakeup timeout |
| `reconnect-delay-ms` | No | `5000` | Reconnection delay |

## Entities Provided

| Entity ID | Description | Unit | Aggregation |
|-----------|-------------|------|-------------|
| `sensor.davis_temp_out` | Outside temperature | °C | mean |
| `sensor.davis_temp_in` | Inside temperature | °C | mean |
| `sensor.davis_humidity_out` | Outside humidity | % | mean |
| `sensor.davis_humidity_in` | Inside humidity | % | mean |
| `sensor.davis_pressure` | Barometric pressure | hPa | mean |
| `sensor.davis_wind_speed` | Wind speed | km/h | mean |
| `sensor.davis_wind_direction` | Wind direction | ° | mean |
| `sensor.davis_wind_gust` | Wind gust | km/h | max |
| `sensor.davis_rain_rate` | Rain rate | mm/h | max |
| `sensor.davis_rain_daily` | Daily rainfall | mm | last |
| `sensor.davis_solar_radiation` | Solar radiation | W/m² | mean |
| `sensor.davis_uv_index` | UV index | - | max |

## Hardware Setup

### Connection Options

1. **Direct Serial**: Connect console to computer via serial cable
2. **USB-Serial Adapter**: Use a quality USB-to-serial adapter
3. **WeatherLink IP**: Connect via network (requires different configuration)

### Serial Port Permissions (Linux)

```bash
# Add user to dialout group
sudo usermod -a -G dialout $USER

# Or set permissions on the device
sudo chmod 666 /dev/ttyUSB0
```

## Architecture

The adapter uses a dedicated thread for serial I/O to prevent blocking operations from affecting other parts of the system:

```
┌─────────────────────────────────────────────────┐
│ DavisAdapter                                     │
├─────────────────────────────────────────────────┤
│                                                  │
│  ┌──────────────┐    ┌──────────────────────┐  │
│  │ Serial Port  │───►│ Reader Thread        │  │
│  │  (Blocking)  │    │ (Dedicated Thread)   │  │
│  └──────────────┘    └──────────┬───────────┘  │
│                                 │              │
│                                 ▼              │
│                      ┌──────────────────────┐  │
│                      │ Event Publisher      │  │
│                      │ (Non-blocking)       │  │
│                      └──────────┬───────────┘  │
│                                 │              │
└─────────────────────────────────┼──────────────┘
                                  │
                                  ▼
                          ┌───────────────┐
                          │  Message Bus  │
                          └───────────────┘
```

## Recovery Support

The Davis console stores approximately 2560 archive records (about 9 days at 5-minute intervals). The adapter can download missed data after system downtime using the DMPAFT command.

## Troubleshooting

### Common Issues

1. **Port not found**: Check serial port name and permissions
2. **No data received**: Ensure console is powered and cable is connected
3. **CRC errors**: Check cable quality and baud rate setting
4. **Timeout errors**: Console may be in setup mode; exit all menus

### Debug Logging

Enable debug logging for troubleshooting:

```yaml
logger:
  levels:
    owl.adapter.davis-serial: DEBUG
```

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.
