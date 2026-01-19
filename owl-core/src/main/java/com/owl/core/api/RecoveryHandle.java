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
package com.owl.core.api;

import java.util.Optional;

/**
 * Handle for tracking recovery operations.
 * <p>
 * Returned by adapters that support data recovery/backfill.
 * Allows the framework to track progress and wait for completion.
 */
public interface RecoveryHandle {

    /**
     * Recovery operation states.
     */
    enum State {
        /** Recovery requested but not started. */
        PENDING,
        /** Recovery in progress. */
        RUNNING,
        /** Recovery finished successfully. */
        COMPLETED,
        /** Recovery failed. */
        FAILED
    }

    /**
     * Get current recovery state.
     *
     * @return recovery state
     */
    State getState();

    /**
     * Get progress as percentage (0-100).
     *
     * @return progress percentage, or empty if unknown
     */
    Optional<Integer> getProgress();

    /**
     * Get number of records recovered so far.
     *
     * @return records recovered
     */
    long getRecordsRecovered();

    /**
     * Get error message if recovery failed.
     *
     * @return error message, or empty if no error
     */
    Optional<String> getError();

    /**
     * Cancel the recovery operation if still running.
     */
    void cancel();

    /**
     * Block until recovery completes or fails.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    void await() throws InterruptedException;

    /**
     * Check if recovery is complete (either successfully or with failure).
     *
     * @return true if recovery is done
     */
    default boolean isDone() {
        State state = getState();
        return state == State.COMPLETED || state == State.FAILED;
    }
}
