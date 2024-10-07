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
package de.bmarwell.aktienfinder.scraper.app;

import de.bmarwell.aktienfinder.scraper.library.scrape.ScrapeService;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "scrape", header = "Scrapes stocks from aktienfinder.net")
public class ScrapeCommand implements Callable<Integer> {

    @Option(
            names = {"-i", "--stocks"},
            description = "stock isins",
            split = ",")
    Set<String> stockIsins;

    @Override
    public Integer call() throws Exception {
        final var scrapeService = new ScrapeService();

        List<AktienfinderStock> ratings = scrapeService.scrapeAll(stockIsins);

        System.out.println(ratings);

        return 0;
    }
}
