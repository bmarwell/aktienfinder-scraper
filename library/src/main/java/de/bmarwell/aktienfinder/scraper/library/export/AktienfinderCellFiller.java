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

import de.bmarwell.aktienfinder.scraper.library.lang.TriConsumer;
import de.bmarwell.aktienfinder.scraper.library.scrape.value.AktienfinderStock;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;

public record AktienfinderCellFiller(
        Function<AktienfinderStock, Object> valueExtractor,
        TriConsumer<Cell, AktienfinderStock, Object> cellConsumer,
        BiFunction<AktienfinderStock, Workbook, CellStyle> styler) {

    public static AktienfinderCellFiller neutral(Function<AktienfinderStock, Object> valueExtractor) {
        return new AktienfinderCellFiller(
                valueExtractor, text, (af, wb) -> new DefaultStyler().apply(valueExtractor, af, wb));
    }

    public static AktienfinderCellFiller withLink(Function<AktienfinderStock, Object> valueExtractor) {
        return new AktienfinderCellFiller(
                valueExtractor, linker, (af, wb) -> new DefaultStyler().apply(valueExtractor, af, wb));
    }

    public static AktienfinderCellFiller fromBewertung(Function<AktienfinderStock, Object> valueExtractor) {
        return new AktienfinderCellFiller(valueExtractor, text, bewertungsStyler);
    }

    public static AktienfinderCellFiller fromZusammenfassung(Function<AktienfinderStock, Object> valueExtractor) {
        return new AktienfinderCellFiller(valueExtractor, text, summaryStyler);
    }

    static TriConsumer<Cell, AktienfinderStock, Object> text = (cell, af, value) -> {
        switch (value) {
            case String s -> cell.setCellValue(s);
            case BigDecimal bd -> cell.setCellValue(bd.doubleValue());
            case Integer n -> cell.setCellValue(n.doubleValue());
            case Short sh -> cell.setCellValue(sh);
            case Double d -> cell.setCellValue(d);
            case Float f -> cell.setCellValue(f);
            default -> cell.setCellValue(value.toString());
        }
    };

    static TriConsumer<Cell, AktienfinderStock, Object> linker = text.andThen((cell, af, value) -> {
        Hyperlink link =
                cell.getRow().getSheet().getWorkbook().getCreationHelper().createHyperlink(HyperlinkType.URL);
        String urlName =
                URLEncoder.encode(af.stock().name(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        link.setAddress("https://aktienfinder.net/aktien-profil/" + urlName + "-Aktie");
        cell.setHyperlink(link);
    });

    static BiFunction<AktienfinderStock, Workbook, CellStyle> bewertungsStyler = (aktienfinderStock, workbook) -> {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        String bewertung = aktienfinderStock.stockFazit().bewertung();

        switch (bewertung) {
            case "stark unterbewertet" -> cellStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            case "unterbewertet" -> cellStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            case "fair bewertet" -> cellStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            case "leicht überbewertet" -> cellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            case "überbewertet" -> cellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            case "stark überbewertet" -> cellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            default -> cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }

        return cellStyle;
    };

    static BiFunction<AktienfinderStock, Workbook, CellStyle> summaryStyler = (aktienfinderStock, workbook) -> {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        String summary = aktienfinderStock.stockFazit().zusammenfassung();

        switch (summary) {
            case "negative" -> cellStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            case "negative-light" -> cellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            case "neutral-light" -> cellStyle.setFillForegroundColor(IndexedColors.GREY_40_PERCENT.getIndex());
            case "positive-light" -> cellStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            case "positive" -> cellStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            default -> cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }

        return cellStyle;
    };
}
