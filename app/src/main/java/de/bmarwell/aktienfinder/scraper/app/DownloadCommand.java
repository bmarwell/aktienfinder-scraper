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

import de.bmarwell.aktienfinder.scraper.library.Stock;
import de.bmarwell.aktienfinder.scraper.library.StockIndex;
import de.bmarwell.aktienfinder.scraper.library.download.DownloadListService;
import de.bmarwell.aktienfinder.scraper.library.download.StockDownloadOption;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "download", header = "Downloads a list of stocks")
public class DownloadCommand implements Callable<Integer> {

    @Option(
            names = {"-o", "--output"},
            description = "Output file (json)")
    Path outputFile;

    @Override
    public Integer call() throws Exception {
        try (DownloadListService downloadListService = new DownloadListService();
                OutputStream outputStream = Files.newOutputStream(
                        outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                JsonWriter jsonWriter = Json.createWriter(outputStream)) {
            StockDownloadOption stockDownloadOption = new StockDownloadOption(StockIndex.all(), 9999);
            List<Stock> stocks = downloadListService.downloadStocks(stockDownloadOption);

            JsonArrayBuilder results = Json.createArrayBuilder();

            for (Stock stock : stocks) {
                JsonObject result = Json.createObjectBuilder()
                        .add("name", Json.createValue(stock.name()))
                        .add("isin", Json.createValue(stock.isin()))
                        .add("index", Json.createValue(stock.index().orElse("")))
                        .build();
                results.add(result);
            }

            JsonObject result =
                    Json.createObjectBuilder().add("results", results.build()).build();

            jsonWriter.writeObject(result);
        }

        return 0;
    }
}
