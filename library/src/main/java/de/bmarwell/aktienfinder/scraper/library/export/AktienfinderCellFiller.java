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
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;

public record AktienfinderCellFiller(
        Function<AktienfinderStock, Object> valueExtractor, BiFunction<AktienfinderStock, Workbook, CellStyle> styler) {

    public static AktienfinderCellFiller neutral(Function<AktienfinderStock, Object> valueExtractor) {
        return new AktienfinderCellFiller(
                valueExtractor, (af, wb) -> new DefaultStyler().apply(valueExtractor, af, wb));
    }

    public static AktienfinderCellFiller withLink(Function<AktienfinderStock, Object> valueExtractor) {
        return new AktienfinderCellFiller(valueExtractor, linker);
    }

    public static AktienfinderCellFiller fromBewertung(Function<AktienfinderStock, Object> valueExtractor) {
        return new AktienfinderCellFiller(valueExtractor, bewertungsStyler);
    }

    public static AktienfinderCellFiller fromZusammenfassung(Function<AktienfinderStock, Object> valueExtractor) {
        return new AktienfinderCellFiller(valueExtractor, summaryStyler);
    }

    static BiFunction<AktienfinderStock, Workbook, CellStyle> linker = (af, workbook) -> {
        CellStyle cellStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName(workbook.getFontAt(0).getFontName());
        font.setFontHeightInPoints(workbook.getFontAt(0).getFontHeightInPoints());
        font.setUnderline(Font.U_SINGLE);
        font.setColor(IndexedColors.BLUE.getIndex());
        cellStyle.setFont(font);

        return cellStyle;
    };

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
