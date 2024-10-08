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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportService {

    private static final Logger LOG = LoggerFactory.getLogger(ExportService.class);

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
            "Bewertung",
            "Zusammenfassung");

    private static final List<HeaderGroup> HEADER_GROUPS = List.of(
            new HeaderGroup(3, "Basis"),
            new HeaderGroup(3, "Basisdaten"),
            new HeaderGroup(3, "Scores"),
            new HeaderGroup(2, "Fazit"));

    private static final List<AktienfinderCellFiller> CELL_FILLERS = List.of(
            // basis
            AktienfinderCellFiller.withLink(((as) -> as.stock().isin())),
            AktienfinderCellFiller.withLink((as) -> as.stock().name()),
            AktienfinderCellFiller.neutral((as) -> as.stock().index().orElse("")),
            // basisdaten
            AktienfinderCellFiller.neutral((as) -> as.stockBewertung().blianzierterGewinn()),
            AktienfinderCellFiller.neutral((as) -> as.stockBewertung().bereinigterGewinn()),
            AktienfinderCellFiller.neutral((as) -> as.stockBewertung().operativerCashFlow()),
            // scores
            AktienfinderCellFiller.neutral(
                    (as) -> as.stockFazit().anlagestrategie().dividendenertragsScore()),
            AktienfinderCellFiller.neutral(
                    (as) -> as.stockFazit().anlagestrategie().dividendenwachstumsScore()),
            AktienfinderCellFiller.neutral(
                    (as) -> as.stockFazit().anlagestrategie().gewinnwachstumsScore()),
            // fazit
            AktienfinderCellFiller.fromBewertung((as) -> as.stockFazit().bewertung()),
            AktienfinderCellFiller.fromZusammenfassung((as) -> as.stockFazit().zusammenfassung()));

    record HeaderGroup(int size, String name) {}

    public void export(List<AktienfinderStock> ratings, Path outputFile) {
        try (Workbook workbook = new XSSFWorkbook();
                var os = Files.newOutputStream(
                        outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            CellStyle headerStyle = createHeaderStyle(workbook);

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

                writeStockRow(afStock, stockRow);
            }

            sheet.setAutoFilter(new CellRangeAddress(1, 1, 0, HEADERS.size()));

            for (int col = 0; col < HEADERS.size(); col++) {
                sheet.autoSizeColumn(col, true);
            }

            workbook.write(os);
        } catch (IOException ioException) {
            LOG.error("Problem writing book to [{}].", outputFile, ioException);
        }
    }

    private static void writeStockRow(AktienfinderStock afStock, Row stockRow) {

        for (int col = 0; col < CELL_FILLERS.size(); col++) {
            try {
                writeRowCell(afStock, stockRow, col);
            } catch (RuntimeException rtEx) {
                LOG.error("Problem writing cell idx [{}] for share [{}].", col, afStock.stock());
            }
        }
    }

    private static void writeRowCell(AktienfinderStock afStock, Row stockRow, int col) {
        AktienfinderCellFiller aktienfinderCellFiller = CELL_FILLERS.get(col);
        Object value = aktienfinderCellFiller.valueExtractor().apply(afStock);
        Cell cellByIndex = stockRow.createCell(col);

        switch (value) {
            case String s -> cellByIndex.setCellValue(s);
            case BigDecimal bd -> cellByIndex.setCellValue(bd.doubleValue());
            case Integer n -> cellByIndex.setCellValue(n.doubleValue());
            case Short sh -> cellByIndex.setCellValue(sh);
            case Double d -> cellByIndex.setCellValue(d);
            case Float f -> cellByIndex.setCellValue(f);
            default -> cellByIndex.setCellValue(value.toString());
        }

        CellStyle style = aktienfinderCellFiller
                .styler()
                .apply(afStock, stockRow.getSheet().getWorkbook());
        cellByIndex.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cellStyle.setAlignment(HorizontalAlignment.CENTER);

        return cellStyle;
    }
}
