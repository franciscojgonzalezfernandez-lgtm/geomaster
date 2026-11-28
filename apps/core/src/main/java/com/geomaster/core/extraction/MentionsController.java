package com.geomaster.core.extraction;

import com.geomaster.core.accounts.AccountRepository;
import com.geomaster.core.api.NotFoundException;
import com.geomaster.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A scan's mentions per answer (extraction-analysis REQ-24), account-scoped. */
@RestController
public class MentionsController {

    private final MentionsReadRepository mentions;
    private final AccountRepository accounts;

    public MentionsController(MentionsReadRepository mentions, AccountRepository accounts) {
        this.mentions = mentions;
        this.accounts = accounts;
    }

    @Operation(operationId = "getScanMentions",
            summary = "Per answer: tracked brands with position, sentiment, recommended flag and evidence, untracked brands and classified citations")
    @ApiResponse(responseCode = "200", description = "Mentions; analysedAt is null until the scan is analysed")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such scan in this account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/v1/scans/{scanId}/mentions")
    public ScanMentions get(@PathVariable UUID scanId) {
        accounts.findForMember(TenantContext.accountId(), TenantContext.userId())
                .orElseThrow(() -> new NotFoundException("account"));
        return mentions.find(scanId).orElseThrow(() -> new NotFoundException("scan"));
    }
}
