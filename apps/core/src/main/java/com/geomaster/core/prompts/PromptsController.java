package com.geomaster.core.prompts;

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

/** Hand-entered prompts of a project (scan-engine REQ-1, REQ-2). */
@RestController
@RequestMapping("/v1/projects/{projectId}/prompts")
public class PromptsController {

    public record CreatePromptRequest(String locale, String topic, String text) {}

    public record UpdatePromptRequest(String topic, String text) {}

    public record PromptResponse(UUID id, String locale, String topic, String text, int version,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        static PromptResponse of(Prompt p) {
            return new PromptResponse(p.id(), p.locale(), p.topic(), p.text(), p.currentVersion(), p.createdAt(), p.updatedAt());
        }
    }

    private final PromptService service;

    public PromptsController(PromptService service) {
        this.service = service;
    }

    @Operation(summary = "Add a prompt (one language) within the plan's prompts limit")
    @ApiResponse(responseCode = "201", description = "Prompt created")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such project in this account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid input (field) or plan limit exceeded (limit, allowed)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<PromptResponse> create(@PathVariable UUID projectId, @RequestBody CreatePromptRequest body) {
        CreatePromptRequest b = body == null ? new CreatePromptRequest(null, null, null) : body;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PromptResponse.of(service.create(projectId, b.locale(), b.topic(), b.text())));
    }

    @Operation(summary = "List the project's active prompts")
    @ApiResponse(responseCode = "200", description = "Prompts")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such project in this account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public List<PromptResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId).stream().map(PromptResponse::of).toList();
    }

    @Operation(summary = "Edit a prompt; new text creates a new version, old answers keep theirs")
    @ApiResponse(responseCode = "200", description = "Updated prompt")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such prompt in this project", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid input (field)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping("/{promptId}")
    public PromptResponse update(@PathVariable UUID projectId, @PathVariable UUID promptId, @RequestBody UpdatePromptRequest body) {
        UpdatePromptRequest b = body == null ? new UpdatePromptRequest(null, null) : body;
        return PromptResponse.of(service.update(projectId, promptId, b.topic(), b.text()));
    }

    @Operation(summary = "Delete a prompt; it is not asked again, past answers stay")
    @ApiResponse(responseCode = "204", description = "Prompt deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such prompt in this project", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{promptId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID promptId) {
        service.delete(projectId, promptId);
        return ResponseEntity.noContent().build();
    }
}
