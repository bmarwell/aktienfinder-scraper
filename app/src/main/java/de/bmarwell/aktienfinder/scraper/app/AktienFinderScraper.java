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

import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "aktienfinder-scraper",
        mixinStandardHelpOptions = true,
        description = "Scrapes Aktienfinder-Scraper.",
        subcommands = {DownloadCommand.class, ScrapeCommand.class, CommandLine.HelpCommand.class})
public class AktienFinderScraper implements Callable<Integer> {

    public static void main(String[] args) {
        // parse args
        CommandLine commandLine = new CommandLine(new AktienFinderScraper());
        CommandLine.ParseResult parseResult = commandLine.parseArgs(args);

        if (parseResult.isUsageHelpRequested() || !parseResult.hasSubcommand()) {
            commandLine.usage(System.out);
            System.exit(0);
        }

        int execute = commandLine.execute(args);
        System.exit(execute);
    }

    @Override
    public Integer call() throws Exception {
        // TODO: implement
        throw new UnsupportedOperationException(
                "not yet implemented: [de.bmarwell.aktienfinder.scraper.app.AktienFinderScraper::call].");
    }
}
