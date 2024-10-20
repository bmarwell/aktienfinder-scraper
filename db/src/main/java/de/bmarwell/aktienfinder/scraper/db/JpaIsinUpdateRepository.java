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
package de.bmarwell.aktienfinder.scraper.db;

import de.bmarwell.aktienfinder.scraper.db.dto.StockBaseData;
import de.bmarwell.aktienfinder.scraper.db.dto.StockBaseData_;
import de.bmarwell.aktienfinder.scraper.value.Isin;
import jakarta.enterprise.context.Dependent;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dependent
@Transactional
public class JpaIsinUpdateRepository extends AbstractRepository implements IsinUpdateRepository {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(JpaIsinUpdateRepository.class);

    public Optional<StockBaseData> getOldestUpdatedEntry() {
        var em = this.getEntityManager();
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(StockBaseData.class);
        Root<StockBaseData> from = query.from(StockBaseData.class);
        query.orderBy(cb.asc(from.get(StockBaseData_.lastUpdateRun)));

        List<StockBaseData> resultList = em.createQuery(query).setMaxResults(1).getResultList();

        if (resultList.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(resultList.getFirst());
    }

    @Override
    public void setUpdatedNowSuccessful(Isin isin, String name) {
        var em = this.getEntityManager();
        StockBaseData stockBaseData = em.find(StockBaseData.class, isin);

        if (stockBaseData == null) {
            log.warn("Cannot update non-existent stock [{}].", isin);
            return;
        }

        stockBaseData.setLastUpdateRun(Instant.now());
        stockBaseData.setName(name);
        stockBaseData.setErrorRunCount(0);
        stockBaseData.setLastSuccessfulRun(Instant.now());
        em.merge(stockBaseData);
        em.flush();
    }

    @Override
    public void setUpdatedNowWithError(Isin isin, Throwable throwable) {
        var em = this.getEntityManager();
        StockBaseData stockBaseData = em.find(StockBaseData.class, isin);

        if (stockBaseData == null) {
            log.warn("Cannot update non-existent stock [{}].", isin);
            return;
        }

        String stackTrace = getStackTrace(throwable);

        log.info("setting stockBaseData: {} to error.", stackTrace.substring(0, Math.min(stackTrace.length(), 100)));

        stockBaseData.setLastUpdateRun(Instant.now());
        stockBaseData.setErrorRunCount(stockBaseData.getErrorRunCount() + 1);
        stockBaseData.setLastErrorRun(Instant.now());
        stockBaseData.setLastErrorMessage(stackTrace);
        em.merge(stockBaseData);
        em.flush();
    }

    private static String getStackTrace(Throwable throwable) {
        var stackTracePrinter = new ByteArrayOutputStream();
        try (PrintWriter printWriter = new PrintWriter(stackTracePrinter, true, StandardCharsets.UTF_8)) {
            throwable.printStackTrace(printWriter);
            printWriter.flush();
            return stackTracePrinter.toString(StandardCharsets.UTF_8);
        } catch (RuntimeException printEx) {
            log.error("Failed to write stack trace.", printEx);

            if (throwable != null) {
                return throwable.getClass().getName() + ": " + throwable.getMessage();
            } else {
                return "Unknown error.";
            }
        }
    }

    @Override
    public StockBaseData addStock(Isin isin, String name) {
        var em = this.getEntityManager();

        StockBaseData existing = em.find(StockBaseData.class, isin);
        if (existing != null) {
            log.warn("Cannot add stock [{}] as it already exists.", isin);
            return existing;
        }

        StockBaseData stockBaseData = new StockBaseData(isin, name, Instant.EPOCH);

        em.persist(stockBaseData);
        em.flush();

        return stockBaseData;
    }

    @Override
    public Optional<StockBaseData> getLatestError() {
        var em = this.getEntityManager();
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(StockBaseData.class);
        Root<StockBaseData> from = query.from(StockBaseData.class);
        query.orderBy(cb.asc(from.get(StockBaseData_.lastUpdateRun)));

        List<StockBaseData> resultList = em.createQuery(query).setMaxResults(1).getResultList();

        if (resultList.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(resultList.getFirst());
    }
}
