package io.littlehorse.usertasks.controllers;

import io.littlehorse.usertasks.services.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(
        name = "Init Controller",
        description = "This is a controller that exposes an endpoint in charge of verifying the Tenant-OIDC configuration"
)
@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated()")
@Slf4j
public class InitController {

    private final TenantService tenantService;

    public InitController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Operation(
            summary = "Initialize Tenant-OIDC integration",
            description = "Checks that the integration between your Identity Provider and LittleHorse Server is valid"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Tenant and Identity Provider are well configured.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid.",
                    content = @Content
            )
    })
    @GetMapping("/{tenant_id}/init")
    public void initIntegrationForTenant(@RequestHeader("Authorization") String accessToken,
                                         @PathVariable(name = "tenant_id") String tenantId) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        log.info("Integration successfully initiated!");
    }

}
