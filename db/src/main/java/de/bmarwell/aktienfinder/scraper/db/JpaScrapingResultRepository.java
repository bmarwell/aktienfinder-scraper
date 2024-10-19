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

import de.bmarwell.aktienfinder.scraper.db.mapper.ScrapingResultMapper;
import de.bmarwell.aktienfinder.scraper.value.AktienfinderStock;
import jakarta.enterprise.context.Dependent;
import jakarta.transaction.Transactional;

@Dependent
@Transactional
public class JpaScrapingResultRepository extends AbstractRepository implements ScrapingResultRepository {

    @Override
    public void updateScrapingResult(AktienfinderStock aktienfinderStock) {
        var em = this.getEntityManager();
        var scrapingResult = ScrapingResultMapper.toDto(aktienfinderStock);
        em.merge(scrapingResult);
    }
}
