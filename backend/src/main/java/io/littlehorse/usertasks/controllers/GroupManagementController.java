package io.littlehorse.usertasks.controllers;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.ACCESS_TOKEN_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.USER_GROUP_ID_MAP_KEY;
import static io.littlehorse.usertasks.util.constants.AuthoritiesConstants.LH_USER_TASKS_ADMIN_ROLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties;
import io.littlehorse.usertasks.configurations.IdentityProviderConfigProperties;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.requests.CreateGroupRequest;
import io.littlehorse.usertasks.models.requests.UpdateGroupRequest;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.IDPGroupDTO;
import io.littlehorse.usertasks.models.responses.IDPGroupListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.services.GroupManagementService;
import io.littlehorse.usertasks.services.TenantService;
import io.littlehorse.usertasks.services.UserTaskService;
import io.littlehorse.usertasks.util.enums.UserTaskStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.*;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(
        name = "Group Management Controller",
        description =
                "This is a controller that exposes endpoints in charge of handling requests related to managing groups")
@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated() && hasAuthority('" + LH_USER_TASKS_ADMIN_ROLE + "')")
@Slf4j
public class GroupManagementController {
    private final TenantService tenantService;
    private final GroupManagementService groupManagementService;
    private final UserTaskService userTaskService;
    private final IdentityProviderConfigProperties identityProviderConfigProperties;

    public GroupManagementController(
            TenantService tenantService,
            GroupManagementService groupManagementService,
            UserTaskService userTaskService,
            IdentityProviderConfigProperties identityProviderConfigProperties) {
        this.tenantService = tenantService;
        this.groupManagementService = groupManagementService;
        this.userTaskService = userTaskService;
        this.identityProviderConfigProperties = identityProviderConfigProperties;
    }

    @Operation(summary = "Create Group", description = "Creates a Group within a specific tenant's IdP")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "201", content = @Content),
                @ApiResponse(
                        responseCode = "401",
                        description = "Tenant Id is not valid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "406",
                        description = "Unknown Identity vendor.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "409",
                        description = "Name is already used by an existing group.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @PostMapping("/{tenant_id}/management/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public void createGroup(
            @RequestHeader(name = "Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @RequestBody CreateGroupRequest requestBody) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler =
                    getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            validateRequestBody(requestBody);

            groupManagementService.createGroupInIdentityProvider(accessToken, requestBody, identityProviderHandler);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to create a group.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(summary = "Get Groups", description = "Gets a collection of Groups within a specific tenant's IdP")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "List of unique Groups",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = IDPGroupListDTO.class))
                        }),
                @ApiResponse(
                        responseCode = "401",
                        description = "Tenant Id is not valid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "406",
                        description = "Unknown Identity vendor.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @GetMapping("/{tenant_id}/management/groups")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<IDPGroupListDTO> getGroups(
            @RequestHeader(name = "Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @RequestParam(required = false) String name,
            @RequestParam(name = "first_result", defaultValue = "0") Integer firstResult,
            @RequestParam(name = "max_results", defaultValue = "10") Integer maxResults) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler =
                    getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            Set<IDPGroupDTO> groups = groupManagementService.getGroups(
                    accessToken, name, firstResult, maxResults, identityProviderHandler);

            IDPGroupListDTO response = new IDPGroupListDTO(groups);

            return ResponseEntity.ok(response);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch groups.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(summary = "Update Group", description = "Updates a Group within a specific tenant's IdP")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", content = @Content),
                @ApiResponse(
                        responseCode = "401",
                        description = "Tenant Id is not valid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "406",
                        description = "Unknown Identity vendor.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "Group cannot be updated because there are UserTaskRuns already assigned to it and waiting to be claimed."
                                        + "Or, name is already being used by an existing group.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @PutMapping("/{tenant_id}/management/groups/{group_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateGroup(
            @RequestHeader(name = "Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "group_id") String groupId,
            @RequestParam(name = "ignore_orphan_tasks", required = false) boolean ignoreOrphanTasks,
            @RequestBody UpdateGroupRequest request) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler =
                    getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            validateRequestBody(request);

            Map<String, Object> lookupParams =
                    Map.of(ACCESS_TOKEN_MAP_KEY, accessToken, USER_GROUP_ID_MAP_KEY, groupId);

            UserGroupDTO userGroup = identityProviderHandler.getUserGroup(lookupParams);

            if (!ignoreOrphanTasks) {
                validateCurrentlyAssignedUserTaskRuns(tenantId, userGroup.getName());
            }

            groupManagementService.updateGroup(accessToken, groupId, request, identityProviderHandler);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to update group.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(summary = "Delete Group", description = "Deletes a Group within a specific tenant's IdP")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", content = @Content),
                @ApiResponse(
                        responseCode = "401",
                        description = "Tenant Id is not valid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "406",
                        description = "Unknown Identity vendor.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "Group cannot be deleted because there are UserTaskRuns already assigned to it and waiting to be claimed.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @DeleteMapping("/{tenant_id}/management/groups/{group_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(
            @RequestHeader(name = "Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "group_id") String groupId,
            @RequestParam(name = "ignore_orphan_tasks", required = false) boolean ignoreOrphanTasks) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties customIdentityProviderProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            IStandardIdentityProviderAdapter identityProviderHandler =
                    getIdentityProviderHandler(customIdentityProviderProperties.getVendor());

            Map<String, Object> lookupParams =
                    Map.of(ACCESS_TOKEN_MAP_KEY, accessToken, USER_GROUP_ID_MAP_KEY, groupId);

            UserGroupDTO userGroup = identityProviderHandler.getUserGroup(lookupParams);

            if (!ignoreOrphanTasks) {
                validateCurrentlyAssignedUserTaskRuns(tenantId, userGroup.getName());
            }

            groupManagementService.deleteGroup(accessToken, groupId, identityProviderHandler);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to delete group.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private IStandardIdentityProviderAdapter getIdentityProviderHandler(@NonNull IdentityProviderVendor vendor) {
        if (vendor == IdentityProviderVendor.KEYCLOAK) {
            return new KeycloakAdapter();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
        }
    }

    private <T> void validateRequestBody(T requestBody) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<T>> constraintViolations = validator.validate(requestBody);

            if (!CollectionUtils.isEmpty(constraintViolations)) {
                String validationMessage = constraintViolations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining("; "));

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validationMessage);
            }
        }
    }

    private void validateCurrentlyAssignedUserTaskRuns(String tenantId, String groupName) {
        UserTaskRequestFilter requestFilter = UserTaskRequestFilter.builder()
                .status(UserTaskStatus.UNASSIGNED)
                .build();

        UserTaskRunListDTO pendingTasks =
                userTaskService.getTasks(tenantId, null, groupName, requestFilter, 1, null, true);

        if (!CollectionUtils.isEmpty(pendingTasks.getUserTasks())) {
            throw new ValidationException(
                    "Cannot rename/delete Group with Task(s) assigned that are pending to be claimed!");
        }
    }
}
