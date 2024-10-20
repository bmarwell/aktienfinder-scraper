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

import java.util.Locale;

/**
 * Represents an International Securities Identification Number (ISIN).
 *
 * <p>Provides methods for validating, parsing, and accessing components of an ISIN.</p>
 */
public record Isin(String value) {

    public Isin(String value) {
        this.value = value.toUpperCase(Locale.ROOT);
        this.validate();
    }

    public static Isin fromString(String isin) {
        return new Isin(isin);
    }

    /**
     * Validates the ISIN.
     *
     * @throws IllegalArgumentException If the ISIN is invalid.
     */
    private void validate() {
        if (value.length() != 12) {
            throw new IllegalArgumentException("ISIN must be 12 characters long.");
        }
        if (!value.matches("[A-Z0-9]{12}")) {
            throw new IllegalArgumentException("ISIN must contain only uppercase letters and numbers.");
        }
    }

    /**
     * Returns the country code of the ISIN.
     *
     * @return The country code of the ISIN.
     */
    public String getCountryCode() {
        return value.substring(0, 2);
    }

    /**
     * Returns the national security identifier of the ISIN.
     *
     * @return The national security identifier of the ISIN.
     */
    public String getNationalSecurityIdentifier() {
        return value.substring(2, 11);
    }

    /**
     * Returns the check digit of the ISIN.
     *
     * @return The check digit of the ISIN.
     */
    public char getCheckDigit() {
        return value.charAt(11);
    }
}
