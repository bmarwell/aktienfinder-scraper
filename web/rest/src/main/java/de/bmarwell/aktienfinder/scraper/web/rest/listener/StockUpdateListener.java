/*
 * Copyright (C) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.bmarwell.aktienfinder.scraper.web.rest.listener;

import de.bmarwell.aktienfinder.scraper.db.IsinUpdateRepository;
import de.bmarwell.aktienfinder.scraper.db.ScrapingResultRepository;
import de.bmarwell.aktienfinder.scraper.db.dto.StockBaseData;
import de.bmarwell.aktienfinder.scraper.library.scrape.ScrapeService;
import de.bmarwell.aktienfinder.scraper.value.AktienfinderStock;
import de.bmarwell.aktienfinder.scraper.value.Stock;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
@Singleton
public class StockUpdateListener implements ServletContextListener {

    private Logger log = LoggerFactory.getLogger(StockUpdateListener.class);

    @Resource
    private ManagedScheduledExecutorService executor;

    @Inject
    IsinUpdateRepository isinUpdateRepository;

    @Inject
    ScrapingResultRepository scrapingResultRepository;

    public StockUpdateListener() {
        // cdi
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        var isinUpdateRepository1 = this.isinUpdateRepository;
        var resultRepository = this.scrapingResultRepository;
        this.executor.scheduleAtFixedRate(
                () -> updateOldestEntry(isinUpdateRepository1, resultRepository), 5L, 60L, TimeUnit.SECONDS);
    }

    private void updateOldestEntry(IsinUpdateRepository baseRepo, ScrapingResultRepository resultRepository) {
        try {
            Optional<StockBaseData> oldestUpdatedEntry = baseRepo.getOldestUpdatedEntry();
            if (oldestUpdatedEntry.isEmpty()) {
                return;
            }

            StockBaseData stockBaseData = oldestUpdatedEntry.orElseThrow();
            Instant lastUpdated = stockBaseData.getLastUpdated();

            Instant onlyUpdateIfBefore = Instant.now().minusSeconds(600L);
            if (lastUpdated.isAfter(onlyUpdateIfBefore)) {
                log.info("Skipping update of {} as it is not 10 minutes old.", stockBaseData.getIsin());
                return;
            }

            log.info("Updating oldest entry {}", stockBaseData.getIsin());

            try (ScrapeService scrapeService = new ScrapeService()) {
                var stock = new Stock(
                        stockBaseData.getName(), stockBaseData.getIsin().toString(), Optional.empty());
                Optional<AktienfinderStock> aktienfinderStock = scrapeService.scrape(stock);

                if (aktienfinderStock.isEmpty()) {
                    // todo: should depend on resuly type (error vs. no data)
                    baseRepo.setUpdatedNow(stockBaseData.getIsin(), stockBaseData.getName());
                    return;
                }

                AktienfinderStock stockResult = aktienfinderStock.orElseThrow();
                resultRepository.updateScrapingResult(stockResult);
                // only then:
                baseRepo.setUpdatedNow(
                        stockBaseData.getIsin(), stockResult.stock().name());
            } catch (Exception e) {
                log.error("error while updating stock [{}]", stockBaseData, e);
            }
        } catch (RuntimeException e) {
            log.error("Error while updating oldest entry", e);
        }
    }

    public void setExecutor(ManagedScheduledExecutorService executor) {
        this.executor = executor;
    }
}
