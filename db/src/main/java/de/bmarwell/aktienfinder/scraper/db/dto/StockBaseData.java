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
import de.bmarwell.aktienfinder.scraper.value.Isin;
import jakarta.persistence.Basic;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Objects;

@Entity
public class StockBaseData {

    @Id
    @Basic
    @Convert(converter = IsinConverter.class)
    private Isin isin;

    private String name;

    private Instant lastUpdated;

    protected StockBaseData() {
        // jpa
        this.lastUpdated = Instant.EPOCH;
    }

    public StockBaseData(Isin isin, String name) {
        this.isin = isin;
        this.name = name;
        this.lastUpdated = Instant.EPOCH;
    }

    public StockBaseData(Isin isin, String name, Instant lastUpdated) {
        this.isin = isin;
        this.name = name;
        this.lastUpdated = lastUpdated;
    }

    public Isin getIsin() {
        return isin;
    }

    public void setIsin(Isin isin) {
        this.isin = isin;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isin);
    }
}
