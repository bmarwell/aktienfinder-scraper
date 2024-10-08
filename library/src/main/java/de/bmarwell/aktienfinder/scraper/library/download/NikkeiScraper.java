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

import java.net.URI;

public class NikkeiScraper extends AbstractBoerseFrankfurtScraper {

    @Override
    public URI uri() {
        return URI.create("https://www.boerse-frankfurt.de/indices/nikkei-225/constituents");
    }

    @Override
    public String getName() {
        return "Nikkei 225";
    }
}
