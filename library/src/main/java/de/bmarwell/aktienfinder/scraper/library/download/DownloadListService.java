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
import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.StockIndex;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache;
import de.bmarwell.aktienfinder.scraper.library.caching.PoorMansCache.Instance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class DownloadListService implements AutoCloseable {

    public final PoorMansCache<Playwright> browsers = new PoorMansCache<>(4, playwrightCreator());

    private Supplier<Playwright> playwrightCreator() {
        return Playwright::create;
    }

    public DownloadListService() {
        // config
    }

    public List<Stock> downloadStocks(StockDownloadOption stockDownloadOption) {
        List<Stock> stocks = new ArrayList<>();

        for (StockIndex stockIndex : stockDownloadOption.stockIndices()) {
            try (Instance<Playwright> playwrightInstance = browsers.getBlocking()) {
                Collection<Stock> stocksFromIndex =
                        stockIndex.getStockRetriever().getStocks(playwrightInstance.instance());
                stocks.addAll(stocksFromIndex);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // TODO: replace with unique items

        return List.copyOf(stocks);
    }

    @Override
    public void close() throws Exception {
        //
    }
}
