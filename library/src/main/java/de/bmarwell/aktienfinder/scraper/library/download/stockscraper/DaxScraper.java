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
 * A scraper implementation for retrieving stock data from the DAX index using ComDirect's website.
 *
 * <p>This class extends {@link AbstractComDirectScraper} and provides
 * the specific URI for the DAX index and its name. The {@code uri} method
 * returns the URL specific to the DAX index, and the {@code getName} method
 * returns "DAX".
 *
 * <p>It uses the inherited functionality to navigate through paginated content,
 * accept cookies, and extract stock data.
 */
public class DaxScraper extends AbstractComDirectScraper {

    @Override
    public URI uri() {
        return URI.create("https://www.comdirect.de/inf/indizes/werte/DE000GVEDAX4");
    }

    @Override
    public String getName() {
        return "DAX";
    }
}
