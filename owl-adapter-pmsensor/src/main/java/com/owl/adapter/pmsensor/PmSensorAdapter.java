package com.owl.adapter.pmsensor;

import com.owl.core.api.*;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Particulate matter sensor adapter.
 * <p>
 * Skeleton implementation — to be completed with actual sensor integration.
 */
@Singleton
@Requires(property = "owl.adapters.pmsensor.enabled", value = "true")
public class PmSensorAdapter implements WeatherAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(PmSensorAdapter.class);
    private static final String ADAPTER_NAME = "pmsensor";

    private final MessageBus messageBus;
    private final PmSensorConfiguration config;

    public PmSensorAdapter(MessageBus messageBus, PmSensorConfiguration config) {
        this.messageBus = messageBus;
        this.config = config;
    }

    @Override
    public String getName() { return ADAPTER_NAME; }

    @Override
    public String getDisplayName() { return "PM Sensor Adapter"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public List<EntityDefinition> getProvidedEntities() {
        return List.of(
                EntityDefinition.builder()
                        .entityId("sensor.pm_pm25")
                        .friendlyName("PM2.5")
                        .source(ADAPTER_NAME)
                        .unit("µg/m³")
                        .deviceClass("pm25")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),
                EntityDefinition.builder()
                        .entityId("sensor.pm_pm10")
                        .friendlyName("PM10")
                        .source(ADAPTER_NAME)
                        .unit("µg/m³")
                        .deviceClass("pm10")
                        .aggregation(AggregationMethod.MEAN)
                        .build()
        );
    }

    @PostConstruct
    void start() {
        LOG.info("Starting PM Sensor adapter (sensor={})", config.getSensorId());
        // TODO: Implement actual data fetching
    }

    @PreDestroy
    void stop() {
        LOG.info("Stopping PM Sensor adapter");
    }

    @Override
    public AdapterHealth getHealth() {
        return AdapterHealth.unknown("Not yet implemented");
    }
}
