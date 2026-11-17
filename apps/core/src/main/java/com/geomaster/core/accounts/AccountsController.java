package com.geomaster.core.accounts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Current user and account (REQ-2, REQ-3, REQ-15). Identity always comes from the service token. */
@RestController
@RequestMapping("/v1")
public class AccountsController {

    public record UserResponse(UUID id, String email, String name, String interfaceLocale) {
        static UserResponse of(AppUser u) {
            return new UserResponse(u.id(), u.email(), u.name(), u.interfaceLocale());
        }
    }

    public record AccountResponse(UUID id, String name, String planId) {
        static AccountResponse of(Account a) {
            return new AccountResponse(a.id(), a.name(), a.planId());
        }
    }

    public record ProvisionResponse(UserResponse user, AccountResponse account) {}

    public record UpdateUserRequest(String interfaceLocale) {}

    private final AccountService service;

    public AccountsController(AccountService service) {
        this.service = service;
    }

    /** First-sign-in provisioning: 201 when the personal account was created, 200 when it existed. */
    @Operation(summary = "Provision the signed-in user's personal account (idempotent)")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "200", description = "Account already existed")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/users/me")
    public ResponseEntity<ProvisionResponse> provision() {
        AccountService.Provisioned p = service.provisionCurrentUser();
        return ResponseEntity.status(p.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new ProvisionResponse(UserResponse.of(p.user()), AccountResponse.of(p.account())));
    }

    @Operation(summary = "Set the interface language")
    @ApiResponse(responseCode = "200", description = "Updated user")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Unsupported interface language", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping("/users/me")
    public UserResponse update(@RequestBody UpdateUserRequest body) {
        return UserResponse.of(service.setInterfaceLocale(body == null ? null : body.interfaceLocale()));
    }

    @Operation(summary = "The current account and its plan")
    @ApiResponse(responseCode = "200", description = "Current account")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Not a member of the token's account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/account")
    public AccountResponse account() {
        return AccountResponse.of(service.currentAccount());
    }

    @Operation(summary = "Delete the current account and all of its data")
    @ApiResponse(responseCode = "204", description = "Account deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid service token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not an owner", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Not a member of the token's account", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/account")
    public ResponseEntity<Void> delete() {
        service.deleteCurrentAccount();
        return ResponseEntity.noContent().build();
    }
}
