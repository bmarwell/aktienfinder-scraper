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
package de.bmarwell.aktienfinder.scraper.db.dto;

import de.bmarwell.aktienfinder.scraper.db.converter.IsinConverter;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

@Entity
public class ScrapingResult {

    @Id
    @Basic
    @Convert(converter = IsinConverter.class)
    private String isin;

    @Column(name = "name")
    private String name;

    private @Nullable Double bilanzierterGewinn;

    private @Nullable Double bereinigterGewinn;

    private @Nullable Double operativerCashFlow;

    private @Nullable String fazitBewertung;

    private @Nullable Short dividendenEtragsScore;

    private @Nullable Short dividendenwachstumsScore;

    private @Nullable Short gewinnwachstumsScore;

    private @Nullable String fazitZusammenfassung;

    private @Nullable String finanzenNetRisiko;

    private @Nullable String finanzenNetRisikoBeschreibung;

    private @Nullable BigDecimal beta;

    public ScrapingResult() {
        // jpa
    }

    public ScrapingResult(
            String isin,
            String name,
            @Nullable Double bilanzierterGewinn,
            @Nullable Double bereinigterGewinn,
            @Nullable Double operativerCashFlow,
            @Nullable String fazitBewertung,
            @Nullable Short dividendenEtragsScore,
            @Nullable Short dividendenwachstumsScore,
            @Nullable Short gewinnwachstumsScore,
            @Nullable String fazitZusammenfassung,
            @Nullable String finanzenNetRisiko,
            @Nullable String finanzenNetRisikoBeschreibung,
            @Nullable BigDecimal beta) {
        // create fields and set them for each parameter
        this.isin = isin;
        this.name = name;
        this.bilanzierterGewinn = bilanzierterGewinn;
        this.bereinigterGewinn = bereinigterGewinn;
        this.operativerCashFlow = operativerCashFlow;
        this.fazitBewertung = fazitBewertung;
        this.dividendenEtragsScore = dividendenEtragsScore;
        this.dividendenwachstumsScore = dividendenwachstumsScore;
        this.gewinnwachstumsScore = gewinnwachstumsScore;
        this.fazitZusammenfassung = fazitZusammenfassung;
        this.finanzenNetRisiko = finanzenNetRisiko;
        this.finanzenNetRisikoBeschreibung = finanzenNetRisikoBeschreibung;
        this.beta = beta;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public @Nullable Double getBilanzierterGewinn() {
        return bilanzierterGewinn;
    }

    public void setBilanzierterGewinn(@Nullable Double bilanzierterGewinn) {
        this.bilanzierterGewinn = bilanzierterGewinn;
    }

    public @Nullable Double getBereinigterGewinn() {
        return bereinigterGewinn;
    }

    public void setBereinigterGewinn(@Nullable Double bereinigterGewinn) {
        this.bereinigterGewinn = bereinigterGewinn;
    }

    public @Nullable Double getOperativerCashFlow() {
        return operativerCashFlow;
    }

    public void setOperativerCashFlow(@Nullable Double operativerCashFlow) {
        this.operativerCashFlow = operativerCashFlow;
    }

    public @Nullable String getFazitBewertung() {
        return fazitBewertung;
    }

    public void setFazitBewertung(@Nullable String fazitBewertung) {
        this.fazitBewertung = fazitBewertung;
    }

    public @Nullable Short getDividendenEtragsScore() {
        return dividendenEtragsScore;
    }

    public void setDividendenEtragsScore(@Nullable Short dividendenEtragsScore) {
        this.dividendenEtragsScore = dividendenEtragsScore;
    }

    public @Nullable Short getDividendenwachstumsScore() {
        return dividendenwachstumsScore;
    }

    public void setDividendenwachstumsScore(@Nullable Short dividendenwachstumsScore) {
        this.dividendenwachstumsScore = dividendenwachstumsScore;
    }

    public @Nullable Short getGewinnwachstumsScore() {
        return gewinnwachstumsScore;
    }

    public void setGewinnwachstumsScore(@Nullable Short gewinnwachstumsScore) {
        this.gewinnwachstumsScore = gewinnwachstumsScore;
    }

    public @Nullable String getFazitZusammenfassung() {
        return fazitZusammenfassung;
    }

    public void setFazitZusammenfassung(@Nullable String fazitZusammenfassung) {
        this.fazitZusammenfassung = fazitZusammenfassung;
    }

    public @Nullable String getFinanzenNetRisiko() {
        return finanzenNetRisiko;
    }

    public void setFinanzenNetRisiko(@Nullable String finanzenNetRisiko) {
        this.finanzenNetRisiko = finanzenNetRisiko;
    }

    public @Nullable String getFinanzenNetRisikoBeschreibung() {
        return finanzenNetRisikoBeschreibung;
    }

    public void setFinanzenNetRisikoBeschreibung(@Nullable String finanzenNetRisikoBeschreibung) {
        this.finanzenNetRisikoBeschreibung = finanzenNetRisikoBeschreibung;
    }

    public @Nullable BigDecimal getBeta() {
        return beta;
    }

    public void setBeta(@Nullable BigDecimal beta) {
        this.beta = beta;
    }
}
