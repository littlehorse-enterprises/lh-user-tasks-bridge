package io.littlehorse.usertasks.controllers;

import io.littlehorse.usertasks.models.responses.IdentityProviderListDTO;
import io.littlehorse.usertasks.services.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Public Controller",
        description = "This is a controller that exposes an endpoint that does not require any authentication nor authorization"
)
@RestController
@CrossOrigin
@Slf4j
public class PublicController {
    private final TenantService tenantService;

    public PublicController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Operation(
            summary = "Get Configured IdP(s)",
            description = "Gets the public configuration of the IdP(s) used by a specific tenant"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of unique Identity Provider Configurations",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IdentityProviderListDTO.class))}
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No configuration data was found for the given tenant.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/config")
    public ResponseEntity<IdentityProviderListDTO> getIdentityProviderConfig(@PathVariable(name = "tenant_id") String tenantId) {
        log.atInfo()
                .setMessage("Fetching IdP configurations for tenant: {}")
                .addArgument(tenantId)
                .log();

        IdentityProviderListDTO idpConfigs = tenantService.getTenantIdentityProviderConfig(tenantId);

        return !CollectionUtils.isEmpty(idpConfigs.getProviders())
                ? ResponseEntity.ok(idpConfigs)
                : ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.NOT_FOUND.value())).build();
    }
}
