package io.littlehorse.usertasks.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties;
import io.littlehorse.usertasks.configurations.IdentityProviderConfigProperties;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.requests.CreateManagedUserRequest;
import io.littlehorse.usertasks.models.requests.IDPUserSearchRequestFilter;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import io.littlehorse.usertasks.services.TenantService;
import io.littlehorse.usertasks.services.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;
import static io.littlehorse.usertasks.util.constants.AuthoritiesConstants.LH_USER_TASKS_ADMIN_ROLE;

@Tag(
        name = "User Management Controller",
        description = "This is a controller that exposes endpoints in charge of handling requests related to managing users"
)
@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated() && hasAuthority('" + LH_USER_TASKS_ADMIN_ROLE + "')")
@Slf4j
public class UserManagementController {
    private final TenantService tenantService;
    private final UserManagementService userManagementService;
    private final IdentityProviderConfigProperties identityProviderConfigProperties;

    public UserManagementController(TenantService tenantService, UserManagementService userManagementService, IdentityProviderConfigProperties identityProviderConfigProperties) {
        this.tenantService = tenantService;
        this.userManagementService = userManagementService;
        this.identityProviderConfigProperties = identityProviderConfigProperties;
    }

    @Operation(
            summary = "Get Users from IdP",
            description = "Gets all active Users from a specific identity provider of a specific tenant."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of unique Users.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IDPUserListDTO.class))}
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "406",
                    description = "Unknown Identity vendor.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/management/users")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<IDPUserListDTO> getUsersFromIdP(@RequestHeader(name = "Authorization") String accessToken,
                                                  @PathVariable(name = "tenant_id") String tenantId,
                                                  @RequestParam(name = "email", required = false) String email,
                                                  @RequestParam(name = "first_name", required = false) String firstName,
                                                  @RequestParam(name = "last_name", required = false) String lastName,
                                                  @RequestParam(name = "username", required = false) String username,
                                                  @RequestParam(name = "user_group_id", required = false) String userGroupId,
                                                  @RequestParam(name = "first_result", defaultValue = "0") Integer firstResult,
                                                  @RequestParam(name = "max_results", defaultValue = "10") Integer maxResults) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            var requestFilter = IDPUserSearchRequestFilter.builder()
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .username(username)
                    .userGroupId(userGroupId)
                    .build();

            IDPUserListDTO idpUserListDTO = userManagementService.listUsersFromIdentityProvider(accessToken,
                    identityProviderHandler, requestFilter, firstResult, maxResults);

            return ResponseEntity.ok(idpUserListDTO);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to get users from Identity Provider");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(
            summary = "Create User",
            description = "Creates a User within a specific tenant's IdP"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "406",
                    description = "Unknown Identity vendor.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @PostMapping("/{tenant_id}/management/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@RequestHeader(name = "Authorization") String accessToken,
                           @PathVariable(name = "tenant_id") String tenantId,
                           @RequestBody CreateManagedUserRequest requestBody) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            if (!requestBody.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create User while missing all properties.");
            }

            userManagementService.createUserInIdentityProvider(accessToken, requestBody, identityProviderHandler);

        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to create a user.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    private IStandardIdentityProviderAdapter getIdentityProviderHandler(@NonNull IdentityProviderVendor vendor) {
        if (vendor == IdentityProviderVendor.KEYCLOAK) {
            return new KeycloakAdapter();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
        }
    }
}
