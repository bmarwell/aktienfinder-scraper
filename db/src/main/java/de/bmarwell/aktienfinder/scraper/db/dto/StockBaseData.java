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
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@Entity
public class StockBaseData {

    @Id
    @Basic
    @Convert(converter = IsinConverter.class)
    private Isin isin;

    @Column(name = "name")
    private String name;

    @Column(name = "last_update_run")
    private Instant lastUpdateRun;

    @Column(name = "last_successful_run")
    private @Nullable Instant lastSuccessfulRun;

    @Column(name = "last_error_run")
    private @Nullable Instant lastErrorRun;

    @Column(name = "last_error_message")
    private @Nullable String lastErrorMessage;

    @Column(name = "error_run_count", nullable = true)
    private int errorRunCount = 0;

    protected StockBaseData() {
        // jpa
    }

    public StockBaseData(Isin isin, String name) {
        this.isin = isin;
        this.name = name;
        this.lastUpdateRun = Instant.EPOCH;
    }

    public StockBaseData(Isin isin, String name, @Nullable Instant lastUpdateRun) {
        this.isin = isin;
        this.name = name;
        this.lastUpdateRun = lastUpdateRun;
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

    public @Nullable Instant getLastUpdateRun() {
        return lastUpdateRun;
    }

    public void setLastUpdateRun(@Nullable Instant lastUpdated) {
        this.lastUpdateRun = lastUpdated;
    }

    public @Nullable Instant getLastSuccessfulRun() {
        return lastSuccessfulRun;
    }

    public void setLastSuccessfulRun(@Nullable Instant lastSuccessfulRun) {
        this.lastSuccessfulRun = lastSuccessfulRun;
    }

    public @Nullable Instant getLastErrorRun() {
        return lastErrorRun;
    }

    public void setLastErrorRun(@Nullable Instant lastErrorRun) {
        this.lastErrorRun = lastErrorRun;
    }

    public @Nullable String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(@Nullable String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public int getErrorRunCount() {
        return errorRunCount;
    }

    public void setErrorRunCount(int errorRunCount) {
        this.errorRunCount = errorRunCount;
    }
}
