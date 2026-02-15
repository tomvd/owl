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
package com.owl.core.persistence;

import com.owl.core.api.ShortTermRecord;
import com.owl.core.api.StatisticsAccess;
import com.owl.core.persistence.entity.StatisticsShortTerm;
import com.owl.core.persistence.repository.StatisticsShortTermRepository;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

/**
 * Implementation of {@link StatisticsAccess} backed by the short-term statistics repository.
 */
@Singleton
public class StatisticsAccessImpl implements StatisticsAccess {

    private final StatisticsShortTermRepository repository;

    public StatisticsAccessImpl(StatisticsShortTermRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ShortTermRecord> getShortTermStatistics(Instant start, Instant end) {
        return repository.findByTimeRange(start, end).stream()
                .map(StatisticsAccessImpl::toRecord)
                .toList();
    }

    @Override
    public List<ShortTermRecord> getShortTermStatistics(String entityId, Instant start, Instant end) {
        return repository.findByEntityIdAndTimeRange(entityId, start, end).stream()
                .map(StatisticsAccessImpl::toRecord)
                .toList();
    }

    private static ShortTermRecord toRecord(StatisticsShortTerm entity) {
        return new ShortTermRecord(
                entity.startTs(),
                entity.entityId(),
                entity.mean(),
                entity.min(),
                entity.max(),
                entity.last(),
                entity.sum(),
                entity.count(),
                entity.attributes()
        );
    }
}
