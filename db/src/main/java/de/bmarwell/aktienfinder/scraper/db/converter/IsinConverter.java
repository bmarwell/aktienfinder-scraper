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
package de.bmarwell.aktienfinder.scraper.db.converter;

import de.bmarwell.aktienfinder.scraper.value.Isin;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class IsinConverter implements AttributeConverter<Isin, String> {

    @Override
    public String convertToDatabaseColumn(Isin attribute) {
        return attribute.value();
    }

    @Override
    public Isin convertToEntityAttribute(String dbData) {
        return new Isin(dbData);
    }
}
