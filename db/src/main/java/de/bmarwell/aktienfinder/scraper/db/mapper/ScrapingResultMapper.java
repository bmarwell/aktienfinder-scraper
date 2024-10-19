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
package de.bmarwell.aktienfinder.scraper.db.mapper;

import de.bmarwell.aktienfinder.scraper.db.dto.ScrapingResult;
import de.bmarwell.aktienfinder.scraper.value.AktienfinderStock;

public final class ScrapingResultMapper {

    public static ScrapingResult toDto(AktienfinderStock aktienfinderStock) {
        var base = aktienfinderStock.stock();
        var bewertung = aktienfinderStock.stockBewertung();
        var stockFazit = aktienfinderStock.stockFazit();
        var finanzenNetRisiko = aktienfinderStock.finanzenNetRisiko();

        return new ScrapingResult(
                base.isin(),
                base.name(),
                bewertung.blianzierterGewinn(),
                bewertung.bereinigterGewinn(),
                bewertung.operativerCashFlow(),
                stockFazit.bewertung(),
                stockFazit.anlagestrategie().dividendenertragsScore(),
                stockFazit.anlagestrategie().dividendenwachstumsScore(),
                stockFazit.anlagestrategie().gewinnwachstumsScore(),
                stockFazit.zusammenfassung(),
                finanzenNetRisiko.risiko().orElse(""),
                finanzenNetRisiko.risikoBeschreibung().orElse(""),
                finanzenNetRisiko.beta().orElse(null));
    }
}
