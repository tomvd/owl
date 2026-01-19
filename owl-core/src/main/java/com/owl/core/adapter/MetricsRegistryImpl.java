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
package com.owl.core.adapter;

import com.owl.core.api.MetricsRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Metrics registry implementation using Micrometer.
 */
@Singleton
public class MetricsRegistryImpl implements MetricsRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsRegistryImpl.class);
    private static final String METRIC_PREFIX = "owl.adapter.";

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> gauges = new ConcurrentHashMap<>();

    public MetricsRegistryImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void incrementCounter(String name) {
        incrementCounter(name, 1);
    }

    @Override
    public void incrementCounter(String name, long amount) {
        Counter counter = counters.computeIfAbsent(name, n ->
                Counter.builder(METRIC_PREFIX + n)
                        .description("Adapter counter: " + n)
                        .register(meterRegistry)
        );
        counter.increment(amount);
    }

    @Override
    public void incrementCounter(String name, Map<String, String> tags) {
        String[] tagArray = tags.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new);

        meterRegistry.counter(METRIC_PREFIX + name, tagArray).increment();
    }

    @Override
    public void recordGauge(String name, double value) {
        AtomicReference<Double> gaugeRef = gauges.computeIfAbsent(name, n -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            meterRegistry.gauge(METRIC_PREFIX + n, ref, AtomicReference::get);
            return ref;
        });
        gaugeRef.set(value);
    }

    @Override
    public void registerGauge(String name, Supplier<Number> supplier) {
        meterRegistry.gauge(METRIC_PREFIX + name, supplier, s -> s.get().doubleValue());
    }

    @Override
    public void recordTiming(String name, long durationMs) {
        Timer timer = timers.computeIfAbsent(name, n ->
                Timer.builder(METRIC_PREFIX + n)
                        .description("Adapter timing: " + n)
                        .register(meterRegistry)
        );
        timer.record(Duration.ofMillis(durationMs));
    }

    @Override
    public void recordDistribution(String name, double value) {
        meterRegistry.summary(METRIC_PREFIX + name).record(value);
    }
}
