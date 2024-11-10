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
import io.littlehorse.usertasks.models.common.UserTaskVariableValue;
import io.littlehorse.usertasks.models.requests.AssignmentRequest;
import io.littlehorse.usertasks.models.requests.CompleteUserTaskRequest;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.DetailedUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserGroupListDTO;
import io.littlehorse.usertasks.models.responses.UserListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskDefListDTO;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;
import static io.littlehorse.usertasks.util.constants.AuthoritiesConstants.USER_TASKS_ADMIN;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ALLOWED_TOKEN_CUSTOM_CLAIM;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.AUTHORIZED_PARTY_CLAIM;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ISSUER_URL_CLAIM;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.USER_ID_CLAIM;

@Tag(
        name = "Admin Controller",
        description = "This is a controller that exposes endpoints in charge of handling requests related to Admin users"
)
@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated() && hasAuthority('" + USER_TASKS_ADMIN + "')")
@Slf4j
public class AdminController {
    private final TenantService tenantService;
    private final UserTaskService userTaskService;
    private final IdentityProviderConfigProperties identityProviderConfigProperties;

    public AdminController(TenantService tenantService, UserTaskService userTaskService, IdentityProviderConfigProperties identityProviderConfigProperties) {
        this.tenantService = tenantService;
        this.userTaskService = userTaskService;
        this.identityProviderConfigProperties = identityProviderConfigProperties;
    }

    @Operation(
            summary = "Gets all UserTasks from a specific tenant."
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
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not enough privileges to access this resource.",
                    content = {@Content}
            )
    })
    @GetMapping("/{tenant_id}/admin/tasks")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskRunListDTO> getAllTasks(@RequestHeader("Authorization") String accessToken,
                                                          @PathVariable(name = "tenant_id") String tenantId,
                                                          @RequestParam(name = "earliest_start_date", required = false)
                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                         LocalDateTime earliestStartDate,
                                                          @RequestParam(name = "latest_start_date", required = false)
                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                         LocalDateTime latestStartDate,
                                                          @RequestParam(name = "status", required = false)
                                                         UserTaskStatus status,
                                                          @RequestParam(name = "type")
                                                         String type,
                                                          @RequestParam(name = "limit")
                                                         Integer limit,
                                                          @RequestParam(name = "user_id", required = false)
                                                         String userId,
                                                          @RequestParam(name = "user_group_id", required = false)
                                                         String userGroup,
                                                          @RequestParam(name = "bookmark", required = false)
                                                         String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
            var allowedTenant = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);
            var client = (String) tokenClaims.get(AUTHORIZED_PARTY_CLAIM);

            var additionalFilters = UserTaskRequestFilter.buildUserTaskRequestFilter(earliestStartDate, latestStartDate,
                    status, type);
            byte[] parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            UserTaskRunListDTO response = userTaskService.getTasks(tenantId, userId, userGroup, additionalFilters,
                    limit, parsedBookmark, true);

            CustomIdentityProviderProperties customIdentityProviderProperties = getCustomIdentityProviderProperties(issuerUrl,
                    allowedTenant, client, identityProviderConfigProperties);

            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor(), false);

            boolean hasIdpAdapter = Objects.nonNull(identityProviderHandler);

            if (!CollectionUtils.isEmpty(response.getUserTasks()) && hasIdpAdapter) {
                response.addAssignmentDetails(accessToken, identityProviderHandler);
            }

            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage())).build();
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch all tasks.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        } catch (Exception e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage())).build();
        }
    }

    @Operation(
            summary = "Gets all UserTaskDef from a specific tenant."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of unique UserTaskDefs names. Optionally, it will retrieve a bookmark " +
                            "field that is used for pagination purposes.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserTaskDefListDTO.class))}
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Tenant Id is not valid.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not enough privileges to access this resource.",
                    content = {@Content}
            ),
    })
    @GetMapping("/{tenant_id}/admin/taskTypes")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskDefListDTO> getAllUserTasksDef(@RequestHeader("Authorization") String accessToken,
                                                                 @PathVariable(name = "tenant_id") String tenantId,
                                                                 @RequestParam(name = "limit") Integer limit,
                                                                 @RequestParam(name = "bookmark", required = false)
                                                                 String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            byte[] parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            UserTaskDefListDTO allUserTasksDef = userTaskService.getAllUserTasksDef(tenantId, limit, parsedBookmark);

            return ResponseEntity.ok(allUserTasksDef);
        } catch (Exception e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage())).build();
        }
    }

    @Operation(
            summary = "Gets a UserTask's details, including its definition (UserTaskDef) and events."
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
                    description = "Tenant Id is not valid.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not enough privileges to access this resource.",
                    content = {@Content}
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No UserTask/UserTaskDef data was found in LH Server using the given params.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/admin/tasks/{wf_run_id}/{user_task_guid}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<DetailedUserTaskRunDTO> getUserTaskDetail(@RequestHeader("Authorization") String accessToken,
                                                                    @PathVariable(name = "tenant_id") String tenantId,
                                                                    @PathVariable(name = "wf_run_id") String wfRunId,
                                                                    @PathVariable(name = "user_task_guid") String userTaskRunGuid) {

        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
            var allowedTenant = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);
            var client = (String) tokenClaims.get(AUTHORIZED_PARTY_CLAIM);

            CustomIdentityProviderProperties customIdentityProviderProperties = getCustomIdentityProviderProperties(issuerUrl,
                    allowedTenant, client, identityProviderConfigProperties);

            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(customIdentityProviderProperties.getVendor(), false);
            boolean hasIdpAdapter = Objects.nonNull(identityProviderHandler);

            var optionalUserTaskDetail = userTaskService.getUserTaskDetails(wfRunId, userTaskRunGuid, tenantId,
                    null, null, true);

            optionalUserTaskDetail.ifPresent(detailedUserTaskRunDTO -> {
                if (hasIdpAdapter) {
                    detailedUserTaskRunDTO.addAssignmentDetails(accessToken, identityProviderHandler);
                }
            });

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
            summary = "Completes a UserTask by making it transition to DONE status if the request is successfully processed in " +
                    "LittleHorse Server."
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
                    responseCode = "404",
                    description = "No UserTask/UserTaskDef data was found in LH Server using the given params.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @PostMapping("/{tenant_id}/admin/tasks/{wf_run_id}/{user_task_guid}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeUserTask(@RequestHeader("Authorization") String accessToken,
                                 @PathVariable(name = "tenant_id") String tenantId,
                                 @PathVariable(name = "wf_run_id") String wfRunId,
                                 @PathVariable(name = "user_task_guid") String userTaskRunGuid,
                                 @RequestBody Map<String, UserTaskVariableValue> requestBody) throws JsonProcessingException {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var tokenClaims = TokenUtil.getTokenClaims(accessToken);

        var userIdFromToken = (String) tokenClaims.get(USER_ID_CLAIM);
        CompleteUserTaskRequest request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(requestBody)
                .build();

        userTaskService.completeUserTask(userIdFromToken, request, tenantId, true);
    }

    @Operation(
            summary = "Assigns a UserTaskRun to a User and/or UserGroup."
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
                    responseCode = "412",
                    description = "Failed at a LittleHorse server condition.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @PostMapping("/{tenant_id}/admin/tasks/{wf_run_id}/{user_task_guid}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignUserTask(@RequestHeader(name = "Authorization") String accessToken,
                               @PathVariable(name = "tenant_id") String tenantId,
                               @PathVariable(name = "wf_run_id") String wfRunId,
                               @PathVariable(name = "user_task_guid") String userTaskRunGuid,
                               @RequestBody AssignmentRequest requestBody) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

            var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
            var allowedTenant = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);
            var client = (String) tokenClaims.get(AUTHORIZED_PARTY_CLAIM);

            CustomIdentityProviderProperties actualProperties = getCustomIdentityProviderProperties(issuerUrl, allowedTenant,
                    client, identityProviderConfigProperties);

            //TODO: This condition MUST be updated in the event that we add support to more IdP adapters
            if (actualProperties.getVendor() == IdentityProviderVendor.KEYCLOAK) {
                IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(actualProperties.getVendor(), true);

                Map<String, Object> params = new HashMap<>();
                params.put("userId", requestBody.getUserId());
                params.put("userGroup", requestBody.getUserGroup());
                params.put("accessToken", accessToken);

                identityProviderHandler.validateAssignmentProperties(params);
            }

            userTaskService.assignUserTask(requestBody, wfRunId, userTaskRunGuid, tenantId);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to reassign a Task");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(
            summary = "Cancels a UserTaskRun by making it transition to CANCELLED status without verifying to whom the " +
                    "UserTaskRun is assigned to."
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
                    description = "Tenant Id is not valid.",
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
    @PostMapping("/{tenant_id}/admin/tasks/{wf_run_id}/{user_task_guid}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelUserTask(@RequestHeader("Authorization") String accessToken,
                               @PathVariable(name = "tenant_id") String tenantId,
                               @PathVariable(name = "wf_run_id") String wfRunId,
                               @PathVariable(name = "user_task_guid") String userTaskRunGuid) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        userTaskService.cancelUserTask(wfRunId, userTaskRunGuid, tenantId);
    }

    @Operation(
            summary = "Gets all Groups from a specific identity provider of a specific tenant."
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
                    responseCode = "403",
                    description = "Missing required role.",
                    content = {@Content}
            ),
            @ApiResponse(
                    responseCode = "406",
                    description = "Unknown vendor.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/admin/groups")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserGroupListDTO> getUserGroupsFromIdentityProvider(@RequestHeader(name = "Authorization") String accessToken,
                                                                              @PathVariable(name = "tenant_id") String tenantId) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
            var allowedTenant = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);
            var client = (String) tokenClaims.get(AUTHORIZED_PARTY_CLAIM);

            CustomIdentityProviderProperties actualProperties = getCustomIdentityProviderProperties(issuerUrl, allowedTenant,
                    client, identityProviderConfigProperties);

            Map<String, Object> params = Map.of("accessToken", accessToken);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(actualProperties.getVendor(), true);

            var response = identityProviderHandler.getUserGroups(params);

            return ResponseEntity.ok(response);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch userGroups.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        }
    }

    @Operation(
            summary = "Gets all active Users from a specific identity provider of a specific tenant."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserListDTO.class))}
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
                    responseCode = "403",
                    description = "Missing required role.",
                    content = {@Content}
            ),
            @ApiResponse(
                    responseCode = "406",
                    description = "Unknown vendor.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/admin/users")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserListDTO> getUsersFromIdentityProvider(@RequestHeader(name = "Authorization") String accessToken,
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
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
            var allowedTenant = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);
            var client = (String) tokenClaims.get(AUTHORIZED_PARTY_CLAIM);

            CustomIdentityProviderProperties actualProperties = getCustomIdentityProviderProperties(issuerUrl, allowedTenant,
                    client, identityProviderConfigProperties);

            Map<String, Object> params = new HashMap<>();
            params.put("accessToken", accessToken);
            params.put("email", email);
            params.put("firstName", firstName);
            params.put("lastName", lastName);
            params.put("username", username);
            params.put("userGroupId", userGroupId);
            params.put("firstResult", firstResult);
            params.put("maxResults", maxResults);

            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(actualProperties.getVendor(), true);

            UserListDTO response = identityProviderHandler.getUsers(params);

            return ResponseEntity.ok(response);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch list of users.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        }
    }

    @Operation(
            summary = "Gets a User's basic info."
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
                    responseCode = "403",
                    description = "Missing required role.",
                    content = {@Content}
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User was not found.",
                    content = {@Content}
            ),
            @ApiResponse(
                    responseCode = "406",
                    description = "Unknown vendor.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/admin/users/{user_id}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserDTO> getUserFromIdentityProvider(@RequestHeader(name = "Authorization") String accessToken,
                                                               @PathVariable(name = "tenant_id") String tenantId,
                                                               @PathVariable(name = "user_id") String userId) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
            var allowedTenant = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);
            var client = (String) tokenClaims.get(AUTHORIZED_PARTY_CLAIM);

            CustomIdentityProviderProperties actualProperties = getCustomIdentityProviderProperties(issuerUrl, allowedTenant,
                    client, identityProviderConfigProperties);

            Map<String, Object> params = Map.of("userId", userId, "accessToken", accessToken);
            IStandardIdentityProviderAdapter identityProviderHandler = getIdentityProviderHandler(actualProperties.getVendor(), true);

            UserDTO response = identityProviderHandler.getUserInfo(params);

            return Objects.nonNull(response)
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.NOT_FOUND)).build();
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch a User's info.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)).build();
        } catch (ResponseStatusException e) {
            throw e;
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
