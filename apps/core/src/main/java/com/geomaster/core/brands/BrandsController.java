package com.geomaster.core.brands;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Own brand and competitors of a project (extraction-analysis REQ-1..REQ-3, REQ-23). */
@RestController
@RequestMapping("/v1/projects/{projectId}/brands")
public class BrandsController {

    public record CreateCompetitorRequest(String name, String domain, List<String> aliases) {}

    public record UpdateBrandRequest(String name) {}

    public record CreateAliasRequest(String kind, String value) {}

    public record AliasResponse(UUID id, String kind, String value) {
        static AliasResponse of(Brand.Alias a) {
            return new AliasResponse(a.id(), a.kind(), a.value());
        }
    }

    public record BrandResponse(UUID id, String kind, String name, List<AliasResponse> aliases, OffsetDateTime createdAt) {
        static BrandResponse of(Brand b) {
            return new BrandResponse(b.id(), b.kind(), b.name(), b.aliases().stream().map(AliasResponse::of).toList(), b.createdAt());
        }
    }

    public record BrandsResponse(BrandResponse own, List<BrandResponse> competitors) {}

    private final BrandService service;

    public BrandsController(BrandService service) {
        this.service = service;
    }

    @Operation(summary = "The project's own brand and its competitors, with aliases")
    @ApiResponse(responseCode = "200", description = "Brands")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such project in this account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public BrandsResponse list(@PathVariable UUID projectId) {
        BrandService.ProjectBrands b = service.list(projectId);
        return new BrandsResponse(BrandResponse.of(b.own()), b.competitors().stream().map(BrandResponse::of).toList());
    }

    @Operation(summary = "Add a competitor (name, optional domain and name aliases) within the plan's competitorsPerDomain limit")
    @ApiResponse(responseCode = "201", description = "Competitor created")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such project in this account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid input or matches an existing brand (field), or plan limit exceeded (limit, allowed)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<BrandResponse> addCompetitor(@PathVariable UUID projectId, @RequestBody CreateCompetitorRequest body) {
        CreateCompetitorRequest b = body == null ? new CreateCompetitorRequest(null, null, null) : body;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BrandResponse.of(service.addCompetitor(projectId, b.name(), b.domain(), b.aliases())));
    }

    @Operation(summary = "Rename a brand (own or competitor)")
    @ApiResponse(responseCode = "200", description = "Updated brand")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such brand in this project", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid name or matches another brand (field)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping("/{brandId}")
    public BrandResponse rename(@PathVariable UUID projectId, @PathVariable UUID brandId, @RequestBody UpdateBrandRequest body) {
        return BrandResponse.of(service.rename(projectId, brandId, body == null ? null : body.name()));
    }

    @Operation(summary = "Delete a competitor; past mentions keep it, new scans no longer resolve to it")
    @ApiResponse(responseCode = "204", description = "Competitor deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such brand in this project", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "The own brand cannot be deleted (field)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{brandId}")
    public ResponseEntity<Void> deleteCompetitor(@PathVariable UUID projectId, @PathVariable UUID brandId) {
        service.deleteCompetitor(projectId, brandId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a name or domain alias to a brand")
    @ApiResponse(responseCode = "201", description = "Brand with the new alias")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such brand in this project", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid alias or matches a brand already in the project (field)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{brandId}/aliases")
    public ResponseEntity<BrandResponse> addAlias(@PathVariable UUID projectId, @PathVariable UUID brandId, @RequestBody CreateAliasRequest body) {
        BrandService.AliasInput input = body == null ? null : new BrandService.AliasInput(body.kind(), body.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(BrandResponse.of(service.addAlias(projectId, brandId, input)));
    }

    @Operation(summary = "Delete an alias; it no longer matches from the next resolution")
    @ApiResponse(responseCode = "204", description = "Alias deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such brand or alias in this project", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{brandId}/aliases/{aliasId}")
    public ResponseEntity<Void> deleteAlias(@PathVariable UUID projectId, @PathVariable UUID brandId, @PathVariable UUID aliasId) {
        service.deleteAlias(projectId, brandId, aliasId);
        return ResponseEntity.noContent().build();
    }
}
