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
package de.bmarwell.aktienfinder.scraper.web.rest;

import de.bmarwell.aktienfinder.scraper.db.IsinUpdateRepository;
import de.bmarwell.aktienfinder.scraper.db.dto.StockBaseData;
import de.bmarwell.aktienfinder.scraper.value.Isin;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path(("/stocks"))
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
public class StockManagement {

    public StockManagement() {}

    @Inject
    IsinUpdateRepository isinRepository;

    @POST
    @Path(("/{isin}"))
    public Response addStock(
            @Parameter(description = "ISIN of the stock to add", in = ParameterIn.PATH, name = "isin")
                    @PathParam(value = "isin")
                    Isin isin,
            @Parameter(description = "name of the stock to add", in = ParameterIn.QUERY, name = "name")
                    @QueryParam("name")
                    String name) {
        StockBaseData stockBaseData = isinRepository.addStock(isin, name);

        return Response.ok()
                .entity(Map.of("isin", stockBaseData.getIsin().value(), "name", stockBaseData.getName()))
                .build();
    }

    @GET
    @Path(("/latestError"))
    public Response getLatestError() {
        var latesterror = isinRepository.getLatestError();

        if (latesterror.isEmpty()) {
            return Response.noContent().build();
        }

        var stockBaseData = latesterror.orElseThrow();

        return Response.ok()
                .entity(Map.of(
                        "message",
                                Optional.ofNullable(stockBaseData.getLastErrorMessage())
                                        .orElse(""),
                        "isin", stockBaseData.getIsin().value(),
                        "name", stockBaseData.getName(),
                        "errorCount", stockBaseData.getErrorRunCount(),
                        "lastError",
                                Optional.ofNullable(stockBaseData.getLastErrorRun())
                                        .map(Instant::toString)
                                        .orElse("NEVER"),
                        "lastUpdate",
                                Optional.ofNullable(stockBaseData.getLastUpdateRun())
                                        .map(Instant::toString)
                                        .orElse("NEVER"),
                        "lastSuccess",
                                Optional.ofNullable(stockBaseData.getLastSuccessfulRun())
                                        .map(Instant::toString)
                                        .orElse("NEVER")))
                .build();
    }

    @Inject
    public void setIsinRepository(IsinUpdateRepository isinRepository) {
        this.isinRepository = isinRepository;
    }
}
