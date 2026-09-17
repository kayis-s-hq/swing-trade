/*
 * Copyright 2026 Swing Trade
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

package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.ParityCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Nightly trigger for {@link ParityCheckService} (plan §7.3): re-runs yesterday's decisions for
 * every active strategy variant and compares them against what the live SIGNAL stage actually
 * persisted, alerting on Discord if anything disagrees.
 *
 * <p>Runs after the EOD ingestion/SIGNAL stage job (which finishes well before this) so the
 * previous session's {@code signals} rows are guaranteed to exist to compare against - see
 * {@code EodIngestionScheduler}/{@code JobOrchestratorService} for that pipeline's own schedule.
 */
@Component
public class ParityCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(ParityCheckScheduler.class);
    private static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");

    private final ParityCheckService parityCheckService;
    private final boolean schedulerEnabled;

    public ParityCheckScheduler(ParityCheckService parityCheckService,
                                 @Value("${app.features.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.parityCheckService = parityCheckService;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "${strategy.parity-check.cron:0 0 22 * * MON-FRI}", zone = "Asia/Kolkata")
    public void runNightlyParityCheck() {
        if (!schedulerEnabled) {
            log.debug("Scheduler disabled (app.features.scheduler.enabled=false) - skipping parity check");
            return;
        }
        try {
            var mismatches = parityCheckService.runParityCheck(LocalDate.now(NSE_ZONE));
            if (mismatches.isEmpty()) {
                log.info("Nightly parity check: no mismatches");
            } else {
                log.warn("Nightly parity check: {} mismatch(es) found", mismatches.size());
            }
        } catch (Exception e) {
            log.error("Nightly parity check failed: {}", e.getMessage(), e);
        }
    }
}
