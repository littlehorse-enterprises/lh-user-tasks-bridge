package io.littlehorse.usertasks.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties;
import io.littlehorse.usertasks.configurations.IdentityProviderConfigProperties;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.requests.*;
import io.littlehorse.usertasks.models.responses.IDPUserDTO;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.services.TenantService;
import io.littlehorse.usertasks.services.UserManagementService;
import io.littlehorse.usertasks.services.UserTaskService;
import io.littlehorse.usertasks.util.TokenUtil;
import io.littlehorse.usertasks.util.enums.CustomUserIdClaim;
import io.littlehorse.usertasks.util.enums.UserTaskStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;
import static io.littlehorse.usertasks.util.constants.AuthoritiesConstants.LH_USER_TASKS_ADMIN_ROLE;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.USER_ID_CLAIM;

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
    private final UserTaskService userTaskService;
    private final IdentityProviderConfigProperties identityProviderConfigProperties;

    public UserManagementController(TenantService tenantService, UserManagementService userManagementService, UserTaskService userTaskService,
                                    IdentityProviderConfigProperties identityProviderConfigProperties) {
        this.tenantService = tenantService;
        this.userManagementService = userManagementService;
        this.userTaskService = userTaskService;
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

    @Operation(
            summary = "Set Password",
            description = "Sets or resets a user's password"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
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
    @PutMapping("/{tenant_id}/management/users/{user_id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertPassword(@RequestHeader(name = "Authorization") String accessToken,
                               @PathVariable(name = "tenant_id") String tenantId,
                               @PathVariable(name = "user_id") String userId,
                               @RequestBody UpsertPasswordRequest requestBody) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            validatePasswordUpsertRequest(requestBody);

            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            userManagementService.setPassword(accessToken, userId, requestBody, identityProviderHandler);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to upsert a user's password.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(
            summary = "Get Single User from IdP",
            description = "Gets a User from a specific identity provider of a specific tenant."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User representation",
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
                    responseCode = "404",
                    description = "User data could not be found",
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
    @GetMapping("/{tenant_id}/management/users/{user_id}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<IDPUserDTO> getUserFromIdP(@RequestHeader(name = "Authorization") String accessToken,
                               @PathVariable(name = "tenant_id") String tenantId,
                               @PathVariable(name = "user_id") String userId) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            Optional<IDPUserDTO> optionalUserDTO = userManagementService.getUserFromIdentityProvider(accessToken, userId, identityProviderHandler);

            return optionalUserDTO.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch a user's data.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(
            summary = "Update Managed User",
            description = "Updates a user's properties"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
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
    @PutMapping("/{tenant_id}/management/users/{user_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUser(@RequestHeader(name = "Authorization") String accessToken,
                               @PathVariable(name = "tenant_id") String tenantId,
                               @PathVariable(name = "user_id") String userId,
                               @RequestBody UpdateManagedUserRequest requestBody) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            validateUpdateManagedUserRequest(requestBody, customIdentityProviderProperties.getUserIdClaim());

            userManagementService.updateUser(accessToken, userId, requestBody, identityProviderHandler);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to update a user's properties.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(
            summary = "Delete Managed User",
            description = "Deletes a user from the respective Identity Provider"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "User cannot be deleted because there are UserTaskRuns already assigned and waiting to be completed by them. " +
                            "Or, Admin user is forbidden from deleting themselves.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @DeleteMapping("/{tenant_id}/management/users/{user_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@RequestHeader(name = "Authorization") String accessToken,
                           @PathVariable(name = "tenant_id") String tenantId,
                           @PathVariable(name = "user_id") String userId,
                           @RequestParam(required = false) boolean ignoreOrphanTasks) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            String adminUserId = (String) tokenClaims.get(USER_ID_CLAIM);

            if (StringUtils.equalsIgnoreCase(adminUserId, userId.trim())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove yourself as user!");
            }

            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            Map<String, Object> params = Map.of("accessToken", accessToken, "userId", userId);
            IDPUserDTO managedUserDTO = identityProviderHandler.getManagedUser(params);

            if (Objects.isNull(managedUserDTO)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No matching user found!");
            }

            if (!ignoreOrphanTasks) {
                validateCurrentlyAssignedUserTaskRuns(managedUserDTO, tenantId, customIdentityProviderProperties);
            }

            userManagementService.deleteUser(accessToken, userId, identityProviderHandler);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to delete user.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(
            summary = "Assign Admin Role",
            description = "Assigns the Admin role to a specific user."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
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
    @PostMapping("/{tenant_id}/management/users/{user_id}/roles/admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignAdminRole(@RequestHeader(name = "Authorization") String accessToken,
                                @PathVariable(name = "tenant_id") String tenantId,
                                @PathVariable(name = "user_id") String userId) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            userManagementService.assignAdminRole(accessToken, userId, identityProviderHandler);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while assigning admin role.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(
            summary = "Remove Admin Role",
            description = "Removes the Admin role from a specific user."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Admin user is forbidden from self removing as admin.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @DeleteMapping("/{tenant_id}/management/users/{user_id}/roles/admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAdminRole(@RequestHeader(name = "Authorization") String accessToken,
                                @PathVariable(name = "tenant_id") String tenantId,
                                @PathVariable(name = "user_id") String userId) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            String adminUserId = (String) tokenClaims.get(USER_ID_CLAIM);

            if (StringUtils.equalsIgnoreCase(adminUserId, userId.trim())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove yourself as admin!");
            }

            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            userManagementService.removeAdminRole(accessToken, userId, identityProviderHandler);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to remove admin role.");
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

    private void validatePasswordUpsertRequest(UpsertPasswordRequest requestBody) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<UpsertPasswordRequest>> constraintViolations = validator.validate(requestBody);

            if (!CollectionUtils.isEmpty(constraintViolations)) {
                String validationMessage = constraintViolations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining("; "));

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validationMessage);
            }
        }
    }

    private void validateUpdateManagedUserRequest(UpdateManagedUserRequest requestBody, CustomUserIdClaim userIdClaim) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<UpdateManagedUserRequest>> constraintViolations = validator.validate(requestBody);

            if (!CollectionUtils.isEmpty(constraintViolations)) {
                String validationMessage = constraintViolations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining("; "));

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validationMessage);
            }
        }

        if (userIdClaim == CustomUserIdClaim.EMAIL && StringUtils.isBlank(requestBody.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot set email to NULL, empty nor whitespace-only value.");
        }
    }

    private void validateCurrentlyAssignedUserTaskRuns(IDPUserDTO managedUserDTO, String tenantId,
                                                       CustomIdentityProviderProperties customIdentityProviderProperties) {
        String lookupUserId = null;

        switch (customIdentityProviderProperties.getUserIdClaim()) {
            case SUB -> lookupUserId = managedUserDTO.getId();
            case PREFERRED_USERNAME -> lookupUserId = managedUserDTO.getUsername();
            case EMAIL -> lookupUserId = managedUserDTO.getEmail();
        }

        UserTaskRequestFilter requestFilter = UserTaskRequestFilter.builder()
                .status(UserTaskStatus.ASSIGNED)
                .build();

        UserTaskRunListDTO pendingTasks = userTaskService.getTasks(tenantId, lookupUserId, null,
                requestFilter, 1, null, false);

        if (!CollectionUtils.isEmpty(pendingTasks.getUserTasks())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete Users with Task(s) assigned that are pending to be completed!");
        }
    }
}
