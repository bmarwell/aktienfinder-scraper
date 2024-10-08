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

import de.bmarwell.aktienfinder.scraper.library.download.stockscraper.DaxScraper;
import de.bmarwell.aktienfinder.scraper.library.download.stockscraper.DowJonesScraper;
import de.bmarwell.aktienfinder.scraper.library.download.stockscraper.MDaxScraper;
import de.bmarwell.aktienfinder.scraper.library.download.stockscraper.NasdaqScraper;
import de.bmarwell.aktienfinder.scraper.library.download.stockscraper.NikkeiScraper;
import de.bmarwell.aktienfinder.scraper.library.download.stockscraper.SDaxScraper;
import de.bmarwell.aktienfinder.scraper.library.download.stockscraper.SP500Scraper;
import java.util.List;

/**
 * The source indexes to read stock names and their ISINs from. *
 */
public enum StockIndex {
    DAX(new DaxScraper()),
    MDAX(new MDaxScraper()),
    SDAX(new SDaxScraper()),
    DOW_JONES(new DowJonesScraper()),
    SP500(new SP500Scraper()),
    // MSCI_AC_ASEAN(new MsciSouthEastAsiaScraper()),
    NIKKEI_225(new NikkeiScraper()),
    NASDAQ_100(new NasdaqScraper());

    private final StockIndexStockRetriever stockRetriever;

    StockIndex(StockIndexStockRetriever stockIndexStockRetriever) {
        this.stockRetriever = stockIndexStockRetriever;
    }

    public static List<StockIndex> all() {
        return List.of(StockIndex.values());
    }

    public StockIndexStockRetriever getStockRetriever() {
        return stockRetriever;
    }
}
