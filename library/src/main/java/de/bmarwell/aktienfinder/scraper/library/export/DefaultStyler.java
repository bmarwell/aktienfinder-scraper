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
package de.bmarwell.aktienfinder.scraper.library.export;

import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import java.math.BigDecimal;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Workbook;

public class DefaultStyler {

    public CellStyle apply(Function<AktienfinderStock, Object> valueExtractor, AktienfinderStock af, Workbook wb) {
        Object value = valueExtractor.apply(af);

        return switch (value) {
            case BigDecimal bd -> roundToTwoDigits(wb);
            case Double d -> roundToTwoDigits(wb);
            case Float f -> roundToTwoDigits(wb);
            default -> wb.createCellStyle();
        };
    }

    private CellStyle roundToTwoDigits(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0.00"));

        return style;
    }
}
