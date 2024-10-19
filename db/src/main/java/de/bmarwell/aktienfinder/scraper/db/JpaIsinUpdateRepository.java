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
import de.bmarwell.aktienfinder.scraper.value.Isin;
import jakarta.enterprise.context.Dependent;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dependent
@Transactional
public class JpaIsinUpdateRepository extends AbstractRepository implements IsinUpdateRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaIsinUpdateRepository.class);

    public Optional<StockBaseData> getOldestUpdatedEntry() {
        var entityManager = this.getEntityManager();
        TypedQuery<StockBaseData> query = entityManager.createQuery(
                "SELECT su FROM StockBaseData su ORDER BY su.lastUpdated ASC LIMIT 1", StockBaseData.class);

        try {
            return Optional.ofNullable(query.getSingleResult());
        } catch (NoSuchElementException nsee) {
            return Optional.empty();
        }
    }

    @Override
    public void setUpdatedNow(Isin isin, String name) {
        var em = this.getEntityManager();
        StockBaseData stockBaseData = em.find(StockBaseData.class, isin);

        if (stockBaseData == null) {
            log.warn("Cannot update non-existent stock [{}].", isin);
            return;
        }

        stockBaseData.setLastUpdated(Instant.now());
        stockBaseData.setName(name);
        em.merge(stockBaseData);
        em.flush();
    }
}
