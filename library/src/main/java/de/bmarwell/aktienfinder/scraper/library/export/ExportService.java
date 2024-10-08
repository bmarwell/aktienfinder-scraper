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
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExportService {

    private static final List<String> HEADERS = List.of(
            "ISIN",
            "Name",
            "Index",
            "Bilanzierter Gewinn",
            "Bereinigter Gewinn",
            "Operativer Cash Flow",
            "Dividendenertragsscore",
            "Dividendenwachstumsscore",
            "Gewinnwachstumsscore",
            "Geeignet",
            "Farbe");

    private static final List<HeaderGroup> HEADER_GROUPS = List.of(
            new HeaderGroup(3, "Basis"),
            new HeaderGroup(3, "Basisdaten"),
            new HeaderGroup(3, "Scores"),
            new HeaderGroup(2, "Fazit"));

    private static final List<Function<AktienfinderStock, Object>> EXTRACTORS = List.of(
            // basis
            (as) -> as.stock().isin(),
            (as) -> as.stock().name(),
            (as) -> as.stock().index().orElse(""),
            // basisdaten
            (as) -> as.stockBewertung().blianzierterGewinn(),
            (as) -> as.stockBewertung().bereinigterGewinn(),
            (as) -> as.stockBewertung().operativerCashFlow(),
            // scores
            (as) -> as.stockFazit().anlagestrategie().dividendenertragsScore(),
            (as) -> as.stockFazit().anlagestrategie().dividendenwachstumsScore(),
            (as) -> as.stockFazit().anlagestrategie().gewinnwachstumsScore(),
            // fazit
            (as) -> as.stockFazit().geeignet(),
            (as) -> as.stockFazit().cssColorName());

    record HeaderGroup(int size, String name) {}

    public void export(List<AktienfinderStock> ratings, Path outputFile) {
        try (Workbook workbook = new XSSFWorkbook();
                var os = Files.newOutputStream(
                        outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle roundToTwoDigits = roundToTwoDigits(workbook);

            Sheet sheet = workbook.createSheet("Aktienfinder");

            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 6000);

            Row headerGroupRow = sheet.createRow(0);

            int idx = 0;
            for (HeaderGroup headerGroup : HEADER_GROUPS) {
                Cell firstCellOfGroup = headerGroupRow.createCell(idx);
                firstCellOfGroup.setCellValue(headerGroup.name());
                firstCellOfGroup.setCellStyle(headerStyle);

                CellRangeAddress headerGroupRegion = new CellRangeAddress(0, 0, idx, idx + headerGroup.size() - 1);
                sheet.addMergedRegion(headerGroupRegion);

                firstCellOfGroup.setCellStyle(headerStyle);

                idx += headerGroup.size();
            }

            Row headerRow = sheet.createRow(1);

            for (int col = 0; col < HEADERS.size(); col++) {
                Cell headerCell = headerRow.createCell(col);
                headerCell.setCellValue(HEADERS.get(col));
                headerCell.setCellStyle(headerStyle);
            }

            for (AktienfinderStock afStock : ratings) {
                Row stockRow = sheet.createRow(sheet.getLastRowNum() + 1);

                for (int col = 0; col < EXTRACTORS.size(); col++) {
                    Object value = EXTRACTORS.get(col).apply(afStock);
                    Cell cellByIndex = stockRow.createCell(col);
                    switch (value) {
                        case String s -> cellByIndex.setCellValue(s);
                        case BigDecimal bd -> {
                            cellByIndex.setCellValue(bd.doubleValue());
                            cellByIndex.setCellStyle(roundToTwoDigits);
                        }
                        case Integer n -> cellByIndex.setCellValue(n.doubleValue());
                        case Short sh -> cellByIndex.setCellValue(sh);
                        case Double d -> {
                            cellByIndex.setCellValue(d);
                            cellByIndex.setCellStyle(roundToTwoDigits);
                        }
                        case Float f -> {
                            cellByIndex.setCellValue(f);
                            cellByIndex.setCellStyle(roundToTwoDigits);
                        }
                        default -> cellByIndex.setCellValue(value.toString());
                    }
                }
            }

            sheet.setAutoFilter(new CellRangeAddress(1, 1, 0, HEADERS.size()));

            for (int col = 0; col < HEADERS.size(); col++) {
                sheet.autoSizeColumn(col, true);
            }

            workbook.write(os);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cellStyle.setFillBackgroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cellStyle.setAlignment(HorizontalAlignment.CENTER);

        return cellStyle;
    }

    private CellStyle roundToTwoDigits(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0.##"));

        return style;
    }
}
