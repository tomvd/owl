package com.owl.adapter.blitzortung;

import com.owl.core.api.*;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Blitzortung lightning detection adapter.
 * <p>
 * Skeleton implementation — to be completed with actual API integration.
 */
@Singleton
@Requires(property = "owl.adapters.blitzortung.enabled", value = "true")
public class BlitzortungAdapter implements WeatherAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(BlitzortungAdapter.class);
    private static final String ADAPTER_NAME = "blitzortung";

    private final MessageBus messageBus;
    private final BlitzortungConfiguration config;

    public BlitzortungAdapter(MessageBus messageBus, BlitzortungConfiguration config) {
        this.messageBus = messageBus;
        this.config = config;
    }

    @Override
    public String getName() { return ADAPTER_NAME; }

    @Override
    public String getDisplayName() { return "Blitzortung Lightning Adapter"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public List<EntityDefinition> getProvidedEntities() {
        return List.of(
                EntityDefinition.builder()
                        .entityId("sensor.blitzortung_lightning_count")
                        .friendlyName("Lightning Strike Count")
                        .source(ADAPTER_NAME)
                        .unit("")
                        .deviceClass(null)
                        .aggregation(AggregationMethod.SUM)
                        .build(),
                EntityDefinition.builder()
                        .entityId("sensor.blitzortung_nearest_distance")
                        .friendlyName("Nearest Lightning Distance")
                        .source(ADAPTER_NAME)
                        .unit("km")
                        .deviceClass("distance")
                        .aggregation(AggregationMethod.MIN)
                        .build()
        );
    }

    @PostConstruct
    void start() {
        LOG.info("Starting Blitzortung adapter (lat={}, lon={}, radius={}km)",
                config.getLatitude(), config.getLongitude(), config.getRadiusKm());
        // TODO: Implement actual data fetching
    }

    @PreDestroy
    void stop() {
        LOG.info("Stopping Blitzortung adapter");
    }

    @Override
    public AdapterHealth getHealth() {
        return AdapterHealth.unknown("Not yet implemented");
    }
}
