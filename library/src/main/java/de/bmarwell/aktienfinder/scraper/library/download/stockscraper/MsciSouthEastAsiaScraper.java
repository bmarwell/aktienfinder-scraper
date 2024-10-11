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
package de.bmarwell.aktienfinder.scraper.library.download.stockscraper;

import java.net.URI;

/**
 * The {@code MsciSouthEastAsiaScraper} class is responsible for scraping stock data from the MSCI AC ASEAN Index.
 * It extends the {@code AbstractWallstreetOnlineDeScraper} class, which provides the necessary functionality
 * to navigate through paginated content and capture stock information.
 *
 * <p>This class specifically targets the URL:
 * {@code https://www.wallstreet-online.de/indizes/msci-ac-asean-index/enthaltenewerte}.
 *
 * <p>Two main operations are defined in this class:
 * <ul>
 *     <li>{@code uri()}: Returns the URL of the page that contains the stock data.
 *     <li>{@code getName()}: Returns the name of the stock index, which is "MSCI AC ASEAN Index".
 * </ul>
 *
 * <p>Extending this class allows it to utilize the functionality of the {@code AbstractWallstreetOnlineDeScraper},
 * including handling cookies, pagination, and extracting stock data from each page.
 */
public class MsciSouthEastAsiaScraper extends AbstractWallstreetOnlineDeScraper {

    @Override
    public URI uri() {
        return URI.create("https://www.wallstreet-online.de/indizes/msci-ac-asean-index/enthaltenewerte");
    }

    @Override
    public String getName() {
        return "MSCI AC ASEAN Index";
    }
}
