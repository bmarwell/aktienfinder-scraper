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
package de.bmarwell.aktienfinder.scraper.library.download;

import com.microsoft.playwright.Playwright;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache.Instance;
import de.bmarwell.aktienfinder.scraper.library.scrape.ExecutorHelper;
import de.bmarwell.aktienfinder.scraper.value.Stock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadListService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadListService.class);

    private final ExecutorService executor = Executors.newWorkStealingPool(ExecutorHelper.getNumberThreads());

    private final PoorMansCache<Playwright> browsers =
            new PoorMansCache<>(ExecutorHelper.getNumberThreads(), playwrightCreator());

    private Supplier<Playwright> playwrightCreator() {
        return Playwright::create;
    }

    public DownloadListService() {
        // config
    }

    public List<Stock> downloadStocks(StockDownloadOption stockDownloadOption) {
        Set<Stock> stocks = new LinkedHashSet<>();
        List<Future<List<Stock>>> threads = new ArrayList<>();

        for (StockIndex stockIndex : stockDownloadOption.stockIndices()) {
            Future<List<Stock>> future = executor.submit(() -> retrieve(stockIndex));
            threads.add(future);
        }

        for (Future<List<Stock>> thread : threads) {
            try {
                List<Stock> resultStocks = thread.get();
                stocks.addAll(resultStocks);
            } catch (InterruptedException | ExecutionException ex) {
                LOG.error("Problem returning thread {}", thread, ex);
            }
        }

        return List.copyOf(stocks);
    }

    private List<Stock> retrieve(StockIndex stockIndex) {
        try (Instance<Playwright> playwrightInstance = browsers.getBlocking()) {
            return stockIndex.getStockRetriever().getStocks(playwrightInstance.instance());
        } catch (Exception e) {
            LOG.error("Problem", e);
            return List.of();
        }
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
        browsers.close();
        executor.shutdownNow();
    }
}
