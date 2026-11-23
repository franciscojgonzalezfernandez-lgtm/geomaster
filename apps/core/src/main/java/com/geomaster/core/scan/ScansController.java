package com.geomaster.core.scan;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Scans API (scan-engine REQ-16, REQ-17). */
@RestController
public class ScansController {

    private final ScanService service;

    public ScansController(ScanService service) {
        this.service = service;
    }

    @Operation(operationId = "startScan", summary = "Start a scan of the project now (queued; the workers run it)")
    @ApiResponse(responseCode = "201", description = "Scan queued")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such project in this account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Scan not started; `reason` is one of already_active, frequency, budget, project_inactive, no_prompts, no_engines", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/v1/projects/{projectId}/scans")
    public ResponseEntity<ScanView> start(@PathVariable UUID projectId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(projectId));
    }

    @Operation(operationId = "listScans", summary = "List the project's scans, newest first (up to 50)")
    @ApiResponse(responseCode = "200", description = "Scans")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such project in this account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/v1/projects/{projectId}/scans")
    public List<ScanView> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @Operation(operationId = "getScan", summary = "A scan with its progress per engine and its cost")
    @ApiResponse(responseCode = "200", description = "Scan")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such scan in this account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/v1/scans/{scanId}")
    public ScanView get(@PathVariable UUID scanId) {
        return service.get(scanId);
    }
}
