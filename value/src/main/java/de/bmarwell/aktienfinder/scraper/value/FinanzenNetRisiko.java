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
package de.bmarwell.aktienfinder.scraper.value;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Repräsentiert das Nettorisiko eines Finanzinstruments.
 *
 * <p>Diese Klasse kapselt Informationen über das Risikoeinstufung eines Finanzinstruments,
 * wie z.B. einer Aktie.
 *
 * @param risiko Die kurze, kategorische Risikoeinstufung (z.B. "Hoch", "Mittel", "Niedrig").
 * @param risikoBeschreibung Eine detaillierte Beschreibung der Risikoeinstufung,
 *                           einschließlich Begründung und ggf. Gültigkeitsdauer.
 * @param beta Ein Maß für die Volatilität des Finanzinstruments im Vergleich zum Gesamtmarkt.
 */
public record FinanzenNetRisiko(
        Optional<String> risiko, Optional<String> risikoBeschreibung, Optional<BigDecimal> beta) {

    public static FinanzenNetRisiko empty() {
        return new FinanzenNetRisiko(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
