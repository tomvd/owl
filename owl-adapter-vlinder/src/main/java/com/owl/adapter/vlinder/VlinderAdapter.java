package com.owl.adapter.vlinder;

import com.owl.core.api.*;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Vlinder weather network adapter.
 * <p>
 * Skeleton implementation — to be completed with actual API integration.
 */
@Singleton
@Requires(property = "owl.adapters.vlinder.enabled", value = "true")
public class VlinderAdapter implements WeatherAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VlinderAdapter.class);
    private static final String ADAPTER_NAME = "vlinder";

    private final MessageBus messageBus;
    private final VlinderConfiguration config;

    public VlinderAdapter(MessageBus messageBus, VlinderConfiguration config) {
        this.messageBus = messageBus;
        this.config = config;
    }

    @Override
    public String getName() { return ADAPTER_NAME; }

    @Override
    public String getDisplayName() { return "Vlinder Weather Network Adapter"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public List<EntityDefinition> getProvidedEntities() {
        return List.of(
                EntityDefinition.builder()
                        .entityId("sensor.vlinder_temperature")
                        .friendlyName("Vlinder Temperature")
                        .source(ADAPTER_NAME)
                        .unit("°C")
                        .deviceClass("temperature")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),
                EntityDefinition.builder()
                        .entityId("sensor.vlinder_humidity")
                        .friendlyName("Vlinder Humidity")
                        .source(ADAPTER_NAME)
                        .unit("%")
                        .deviceClass("humidity")
                        .aggregation(AggregationMethod.MEAN)
                        .build(),
                EntityDefinition.builder()
                        .entityId("sensor.vlinder_wind_speed")
                        .friendlyName("Vlinder Wind Speed")
                        .source(ADAPTER_NAME)
                        .unit("m/s")
                        .deviceClass("wind_speed")
                        .aggregation(AggregationMethod.MEAN)
                        .build()
        );
    }

    @PostConstruct
    void start() {
        LOG.info("Starting Vlinder adapter (station={})", config.getStationId());
        // TODO: Implement actual data fetching
    }

    @PreDestroy
    void stop() {
        LOG.info("Stopping Vlinder adapter");
    }

    @Override
    public AdapterHealth getHealth() {
        return AdapterHealth.unknown("Not yet implemented");
    }
}
