package io.littlehorse.usertasks.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties;
import io.littlehorse.usertasks.configurations.IdentityProviderConfigProperties;
import io.littlehorse.usertasks.exceptions.CustomUnauthorizedException;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.common.UserTaskVariableValue;
import io.littlehorse.usertasks.models.requests.CompleteUserTaskRequest;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.DetailedUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserGroupListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.services.TenantService;
import io.littlehorse.usertasks.services.UserTaskService;
import io.littlehorse.usertasks.util.TokenUtil;
import io.littlehorse.usertasks.util.enums.UserTaskStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.USER_ID_CLAIM;

@Tag(
        name = "User Controller",
        description = "This is a controller that exposes endpoints in charge of handling requests related to non-admin users"
)
@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated()")
@Slf4j
public class UserController {
    private final TenantService tenantService;
    private final UserTaskService userTaskService;
    private final IdentityProviderConfigProperties identityProviderConfigProperties;

    public UserController(TenantService tenantService, UserTaskService userTaskService, IdentityProviderConfigProperties identityProviderConfigProperties) {
        this.tenantService = tenantService;
        this.userTaskService = userTaskService;
        this.identityProviderConfigProperties = identityProviderConfigProperties;
    }

    @Operation(
            summary = "Get UserTasks",
            description = "Gets all UserTasks assigned to a user and/or userGroup that the user belongs to."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of unique UserTasks with some basic attributes. Optionally, it will retrieve a bookmark " +
                            "field that is used for pagination purposes.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserTaskRunListDTO.class))}
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/tasks")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskRunListDTO> getMyTasks(@RequestHeader("Authorization") String accessToken,
                                                         @PathVariable(name = "tenant_id") String tenantId,
                                                         @RequestParam(name = "earliest_start_date", required = false)
                                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                             LocalDateTime earliestStartDate,
                                                         @RequestParam(name = "latest_start_date", required = false)
                                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                             LocalDateTime latestStartDate,
                                                         @RequestParam(name = "status", required = false)
                                                             UserTaskStatus status,
                                                         @RequestParam(name = "type", required = false)
                                                             String type,
                                                         @RequestParam(name = "user_group_id", required = false)
                                                             String userGroupId,
                                                         @RequestParam(name = "limit")
                                                             Integer limit,
                                                         @RequestParam(name = "bookmark", required = false)
                                                             String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

            var additionalFilters = UserTaskRequestFilter.buildUserTaskRequestFilter(earliestStartDate, latestStartDate, status, type);
            var parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            CustomIdentityProviderProperties actualProperties = getCustomIdentityProviderProperties(accessToken,
                    identityProviderConfigProperties);

            var userIdFromToken = (String) tokenClaims.get(actualProperties.getUserIdClaim().toString());

            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(actualProperties.getVendor(), false);

            boolean hasIdPAdapter = Objects.nonNull(identityProviderHandler);

            if (StringUtils.hasText(userGroupId) && hasIdPAdapter) {
                identityProviderHandler.validateUserGroup(userGroupId, accessToken);
                UserGroupDTO foundUserGroup = identityProviderHandler.getUserGroup(Map.of("userGroupId", userGroupId,
                        "accessToken", accessToken));

                if (Objects.nonNull(foundUserGroup)) {
                    userGroupId = foundUserGroup.getName();
                }
            }

            UserTaskRunListDTO response = userTaskService.getTasks(tenantId, userIdFromToken, userGroupId, additionalFilters,
                    limit, parsedBookmark, false);

            if (!CollectionUtils.isEmpty(response.getUserTasks()) && hasIdPAdapter) {
                response.addAssignmentDetails(accessToken, identityProviderHandler, actualProperties);
            }

            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage())).build();
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch UserTaskRuns.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        }
    }

    @Operation(
            summary = "Get UserTask details",
            description = "Gets a UserTask's details, including its definition (UserTaskDef)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "UserTask's details with fields defined in its UserTaskDef. Additionally, it may include " +
                            "results (in case it is in DONE status).",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DetailedUserTaskRunDTO.class))}
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid. It could also be triggered when current user/userGroup does " +
                            "not have permissions to see UserTask details.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No UserTask/UserTaskDef data was found in LH Server using the given params.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<DetailedUserTaskRunDTO> getUserTaskDetail(@RequestHeader("Authorization") String accessToken,
                                                                    @PathVariable(name = "tenant_id") String tenantId,
                                                                    @PathVariable(name = "wf_run_id") String wfRunId,
                                                                    @PathVariable(name = "user_task_guid") String userTaskRunGuid) {

        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            CustomIdentityProviderProperties actualIdPProperties = getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

            var userIdFromToken = (String) tokenClaims.get(actualIdPProperties.getUserIdClaim().toString());

            var optionalUserTaskDetail = userTaskService.getUserTaskDetails(wfRunId, userTaskRunGuid, tenantId, userIdFromToken,
                    null, false);

            if (optionalUserTaskDetail.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.of(optionalUserTaskDetail);
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage())).build();
        } catch (CustomUnauthorizedException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage())).build();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        }
    }

    @Operation(
            summary = "Complete UserTask",
            description = "Completes a UserTask by making it transition to DONE status if the request is successfully processed in " +
                    "LittleHorse Server."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid. It could also be triggered when current user/userGroup does " +
                            "not have permissions to complete the requested UserTask.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No UserTask/UserTaskDef data was found in LH Server using the given params.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @PostMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeUserTask(@RequestHeader("Authorization") String accessToken,
                                 @PathVariable(name = "tenant_id") String tenantId,
                                 @PathVariable(name = "wf_run_id") String wfRunId,
                                 @PathVariable(name = "user_task_guid") String userTaskRunGuid,
                                 @RequestBody Map<String, UserTaskVariableValue> requestBody) throws JsonProcessingException {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        CustomIdentityProviderProperties actualIdPProperties = getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

        var userIdFromToken = (String) tokenClaims.get(actualIdPProperties.getUserIdClaim().toString());
        CompleteUserTaskRequest request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(requestBody)
                .build();

        userTaskService.completeUserTask(userIdFromToken, request, tenantId, false);
    }

    @Operation(
            summary = "Cancel UserTask",
            description = "Cancels a UserTask by making it transition to CANCELLED status if the request is successfully " +
                    "processed in LittleHorse Server."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Field(s) passed in is/are invalid, or no userId nor userGroup are passed in.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid. It could also be triggered when current user/userGroup does " +
                            "not have permissions to complete the requested UserTask.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Trying to cancel a UserTask that is already DONE or CANCELLED",
                    content = {@Content}
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No UserTask data was found in LH Server using the given params.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "412",
                    description = "Failed at a LittleHorse server condition.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @PostMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelUserTask(@RequestHeader("Authorization") String accessToken,
                               @PathVariable(name = "tenant_id") String tenantId,
                               @PathVariable(name = "wf_run_id") String wfRunId,
                               @PathVariable(name = "user_task_guid") String userTaskRunGuid) throws JsonProcessingException {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        CustomIdentityProviderProperties actualIdPProperties = getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

        var userIdFromToken = (String) tokenClaims.get(actualIdPProperties.getUserIdClaim().toString());

        userTaskService.cancelUserTaskForNonAdmin(wfRunId, userTaskRunGuid, tenantId, userIdFromToken);
    }

    @Operation(
            summary = "Claim UserTask",
            description = "Claims a UserTaskRun by assigning it to the requester user."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Field(s) passed in is/are invalid, or no userId nor userGroup are passed in.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid. It could also be triggered when given admin user does not " +
                            "have permissions to assign users/userGroups to the requested UserTask.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No UserTask data was found in LH Server using the given params.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "UserTask cannot be claimed.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "412",
                    description = "Failed at a LittleHorse server condition.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @PostMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void claimUserTask(@RequestHeader("Authorization") String accessToken,
                              @PathVariable(name = "tenant_id") String tenantId,
                              @PathVariable(name = "wf_run_id") String wfRunId,
                              @PathVariable(name = "user_task_guid") String userTaskRunGuid) throws JsonProcessingException {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        CustomIdentityProviderProperties actualProperties = CustomIdentityProviderProperties.getCustomIdentityProviderProperties(accessToken,
                identityProviderConfigProperties);

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

        var userIdFromToken = (String) tokenClaims.get(actualProperties.getUserIdClaim().toString());

        userTaskService.claimUserTask(userIdFromToken, wfRunId, userTaskRunGuid, tenantId);
    }

    @Operation(
            summary = "Get Groups",
            description = "Gets all Groups from a specific identity provider of a specific tenant for the requesting user."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserGroupListDTO.class))}
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Field(s) passed in is/are invalid.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
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
                    description = "Unknown vendor.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/groups")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserGroupListDTO> getUserGroupsFromIdentityProvider(@PathVariable(name = "tenant_id") String tenantId,
                                                                              @RequestHeader(name = "Authorization") String accessToken) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties actualProperties = CustomIdentityProviderProperties.getCustomIdentityProviderProperties(accessToken,
                    identityProviderConfigProperties);

            Map<String, Object> params = Map.of("accessToken", accessToken);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(actualProperties.getVendor(), true);

            var response = identityProviderHandler.getMyUserGroups(params);

            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        }
    }

    @Operation(
            summary = "Get User Info",
            description = "Gets the requesting user's info."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserDTO.class))}
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Field(s) passed in is/are invalid.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
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
                    description = "Unknown vendor.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/userInfo")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserDTO> getMyUserInfo(@RequestHeader(name = "Authorization") String accessToken,
                                                 @PathVariable(name = "tenant_id") String tenantId) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var userId = (String) tokenClaims.get(USER_ID_CLAIM);

            CustomIdentityProviderProperties actualProperties = CustomIdentityProviderProperties.getCustomIdentityProviderProperties(accessToken,
                    identityProviderConfigProperties);

            Map<String, Object> params = Map.of("userId", userId, "accessToken", accessToken);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(actualProperties.getVendor(), true);

            var response = identityProviderHandler.getUserInfo(params);

            return ResponseEntity.ok(response);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch User info.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        }
    }

    @Operation(
            summary = "Get Claimable UserTasks",
            description = "Gets all UserTasks assigned to an specific userGroup that the user belongs to."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of unique UserTasks with some basic attributes. Optionally, it will retrieve a bookmark " +
                            "field that is used for pagination purposes.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserTaskRunListDTO.class))}
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/tasks/claimable")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskRunListDTO> getClaimableTasks(@RequestHeader("Authorization") String accessToken,
                                                         @PathVariable(name = "tenant_id") String tenantId,
                                                         @RequestParam(name = "earliest_start_date", required = false)
                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                         LocalDateTime earliestStartDate,
                                                         @RequestParam(name = "latest_start_date", required = false)
                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                         LocalDateTime latestStartDate,
                                                         @RequestParam(name = "user_group_id")
                                                         String userGroupId,
                                                         @RequestParam(name = "limit")
                                                         Integer limit,
                                                         @RequestParam(name = "bookmark", required = false)
                                                         String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

            //Here we are hardcoding the UNASSIGNED status on purpose since that is the way in which we can fetch claimable tasks from LH Server
            UserTaskStatus claimableStatus = UserTaskStatus.UNASSIGNED;

            var additionalFilters = UserTaskRequestFilter.buildUserTaskRequestFilter(earliestStartDate, latestStartDate, claimableStatus, null);
            var parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            CustomIdentityProviderProperties actualProperties = getCustomIdentityProviderProperties(accessToken,
                    identityProviderConfigProperties);

            var userIdFromToken = (String) tokenClaims.get(actualProperties.getUserIdClaim().toString());

            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(actualProperties.getVendor(), false);

            boolean hasIdPAdapter = Objects.nonNull(identityProviderHandler);

            if (hasIdPAdapter) {
                identityProviderHandler.validateUserGroup(userGroupId, accessToken);
                UserGroupDTO foundUserGroup = identityProviderHandler.getUserGroup(Map.of("userGroupId", userGroupId,
                        "accessToken", accessToken));

                if (Objects.nonNull(foundUserGroup)) {
                    userGroupId = foundUserGroup.getName();
                }
            }

            UserTaskRunListDTO response = userTaskService.getTasks(tenantId, userIdFromToken, userGroupId, additionalFilters,
                    limit, parsedBookmark, false);

            if (!CollectionUtils.isEmpty(response.getUserTasks()) && hasIdPAdapter) {
                response.addAssignmentDetails(accessToken, identityProviderHandler, actualProperties);
            }

            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage())).build();
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch claimable UserTaskRuns.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        }
    }

    private IStandardIdentityProviderAdapter getIdentityProviderHandler(@NonNull IdentityProviderVendor vendor, boolean strict) {
        if (vendor == IdentityProviderVendor.KEYCLOAK) {
            return new KeycloakAdapter();
        } else {
            if (strict) {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
            } else {
                return null;
            }
        }
    }
}
