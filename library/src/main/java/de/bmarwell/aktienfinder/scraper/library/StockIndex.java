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
package de.bmarwell.aktienfinder.scraper.library;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The source indexes to read stock names and their ISINs from. *
 */
public enum StockIndex implements StockIndexStockRetriever {
    NASDAQ_100(new AbstractWikiDataSockIndexRetriever() {
        @Override
        public String getWikiDataQid() {
            return "";
        }

        @Override
        public String getName() {
            return "NASDAQ 100";
        }
    });

    private final StockIndexStockRetriever stockRetriever;

    StockIndex(StockIndexStockRetriever stockIndexStockRetriever) {
        this.stockRetriever = stockIndexStockRetriever;
    }

    public static List<StockIndex> all() {
        return List.of(StockIndex.values());
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public List<Stock> getStocks() {
        return Arrays.stream(values())
                .map(StockIndexStockRetriever::getStocks)
                .flatMap(Collection::stream)
                .toList();
    }
}
