package io.littlehorse.usertasks.controllers;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.USER_ID_CLAIM;

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
import io.littlehorse.usertasks.models.requests.comment_requests.CommentContentRequest;
import io.littlehorse.usertasks.models.requests.comment_requests.DeleteCommentRequest;
import io.littlehorse.usertasks.models.requests.comment_requests.EditCommentRequest;
import io.littlehorse.usertasks.models.requests.comment_requests.PutCommentRequest;
import io.littlehorse.usertasks.models.responses.AuditEventDTO;
import io.littlehorse.usertasks.models.responses.DetailedUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserGroupListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.services.TenantService;
import io.littlehorse.usertasks.services.UserTaskService;
import io.littlehorse.usertasks.util.TokenUtil;
import io.littlehorse.usertasks.util.enums.UserTaskStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(
        name = "User Controller",
        description =
                "This is a controller that exposes endpoints in charge of handling requests related to non-admin users")
@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated()")
@Slf4j
public class UserController {
    private final TenantService tenantService;
    private final UserTaskService userTaskService;
    private final IdentityProviderConfigProperties identityProviderConfigProperties;
    private final String DELIMITER = "::";

    public UserController(
            TenantService tenantService,
            UserTaskService userTaskService,
            IdentityProviderConfigProperties identityProviderConfigProperties) {
        this.tenantService = tenantService;
        this.userTaskService = userTaskService;
        this.identityProviderConfigProperties = identityProviderConfigProperties;
    }

    @Operation(
            summary = "Get UserTasks",
            description = "Gets all UserTasks assigned to a user and/or userGroup that the user belongs to.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "List of unique UserTasks with some basic attributes. Optionally, it will retrieve a bookmark "
                                        + "field that is used for pagination purposes.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserTaskRunListDTO.class))
                        }),
                @ApiResponse(
                        responseCode = "401",
                        description = "Tenant Id is not valid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @GetMapping("/{tenant_id}/tasks")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskRunListDTO> getMyTasks(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @RequestParam(name = "earliest_start_date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime earliestStartDate,
            @RequestParam(name = "latest_start_date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime latestStartDate,
            @RequestParam(name = "status", required = false) UserTaskStatus status,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "user_group_id", required = false) String userGroupId,
            @RequestParam(name = "limit") Integer limit,
            @RequestParam(name = "bookmark", required = false) String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED))
                        .build();
            }

            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

            var additionalFilters =
                    UserTaskRequestFilter.buildUserTaskRequestFilter(earliestStartDate, latestStartDate, status, type);
            var parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            CustomIdentityProviderProperties actualProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

            var userIdFromToken =
                    (String) tokenClaims.get(actualProperties.getUserIdClaim().toString());

            IStandardIdentityProviderAdapter identityProviderHandler =
                    getIdentityProviderHandler(actualProperties.getVendor(), false);

            boolean hasIdPAdapter = Objects.nonNull(identityProviderHandler);

            if (StringUtils.hasText(userGroupId) && hasIdPAdapter) {
                identityProviderHandler.validateUserGroup(userGroupId, accessToken);
                UserGroupDTO foundUserGroup = identityProviderHandler.getUserGroup(
                        Map.of("userGroupId", userGroupId, "accessToken", accessToken));

                if (Objects.nonNull(foundUserGroup)) {
                    userGroupId = foundUserGroup.getName();
                }
            }

            UserTaskRunListDTO response = userTaskService.getTasks(
                    tenantId, userIdFromToken, userGroupId, additionalFilters, limit, parsedBookmark, false);

            if (!CollectionUtils.isEmpty(response.getUserTasks()) && hasIdPAdapter) {
                response.addAssignmentDetails(accessToken, identityProviderHandler, actualProperties);
            }

            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()))
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch UserTaskRuns.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                    .build();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                    .build();
        }
    }

    @Operation(
            summary = "Get UserTask details",
            description = "Gets a UserTask's details, including its definition (UserTaskDef).")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "UserTask's details with fields defined in its UserTaskDef. Additionally, it may include "
                                        + "results (in case it is in DONE status).",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = DetailedUserTaskRunDTO.class))
                        }),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Tenant Id is not valid. It could also be triggered when current user/userGroup does "
                                        + "not have permissions to see UserTask details.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "404",
                        description = "No UserTask/UserTaskDef data was found in LH Kernel using the given params.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @GetMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<DetailedUserTaskRunDTO> getUserTaskDetail(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "wf_run_id") String wfRunId,
            @PathVariable(name = "user_task_guid") String userTaskRunGuid) {

        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED))
                        .build();
            }

            CustomIdentityProviderProperties actualIdPProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

            var userIdFromToken = (String)
                    tokenClaims.get(actualIdPProperties.getUserIdClaim().toString());

            var optionalUserTaskDetail = userTaskService.getUserTaskDetails(
                    wfRunId, userTaskRunGuid, tenantId, userIdFromToken, null, false);

            if (optionalUserTaskDetail.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.of(optionalUserTaskDetail);
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()))
                    .build();
        } catch (CustomUnauthorizedException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                    .build();
        }
    }

    @Operation(
            summary = "Complete UserTask",
            description =
                    "Completes a UserTask by making it transition to DONE status if the request is successfully processed in "
                            + "the LittleHorse Kernel.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", content = @Content),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Tenant Id is not valid. It could also be triggered when current user/userGroup does "
                                        + "not have permissions to complete the requested UserTask.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "404",
                        description = "No UserTask/UserTaskDef data was found in LH Kernel using the given params.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @PostMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeUserTask(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "wf_run_id") String wfRunId,
            @PathVariable(name = "user_task_guid") String userTaskRunGuid,
            @RequestBody Map<String, UserTaskVariableValue> requestBody)
            throws JsonProcessingException {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        CustomIdentityProviderProperties actualIdPProperties =
                getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

        var userIdFromToken =
                (String) tokenClaims.get(actualIdPProperties.getUserIdClaim().toString());
        CompleteUserTaskRequest request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(requestBody)
                .build();

        userTaskService.completeUserTask(userIdFromToken, request, tenantId, false);
    }

    @Operation(
            summary = "Cancel UserTask",
            description =
                    "Cancels a UserTask by making it transition to CANCELLED status if the request is successfully "
                            + "processed in the LittleHorse Kernel.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", content = @Content),
                @ApiResponse(
                        responseCode = "400",
                        description = "Field(s) passed in is/are invalid, or no userId nor userGroup are passed in.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Tenant Id is not valid. It could also be triggered when current user/userGroup does "
                                        + "not have permissions to complete the requested UserTask.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "403",
                        description = "Trying to cancel a UserTask that is already DONE or CANCELLED",
                        content = {@Content}),
                @ApiResponse(
                        responseCode = "404",
                        description = "No UserTask data was found in LH Kernel using the given params.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "412",
                        description = "Failed at a LittleHorse Kernel condition.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @PostMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelUserTask(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "wf_run_id") String wfRunId,
            @PathVariable(name = "user_task_guid") String userTaskRunGuid)
            throws JsonProcessingException {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        CustomIdentityProviderProperties actualIdPProperties =
                getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

        var userIdFromToken =
                (String) tokenClaims.get(actualIdPProperties.getUserIdClaim().toString());

        userTaskService.cancelUserTaskForNonAdmin(wfRunId, userTaskRunGuid, tenantId, userIdFromToken);
    }

    @Operation(
            summary = "Comment on a UserTask",
            description =
                    "Places a comment on a UserTask. Returns the corresponding AuditEventDTO that represents the comment.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "A comment was successfully added to the UserTask.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AuditEventDTO.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "The input provided was invalid.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - the tenant ID or access token is invalid.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "The UserTaskRun/WfRun was not found.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Internal server error occurred while trying to add a comment.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/comment")
    @ResponseStatus(HttpStatus.OK)
    public AuditEventDTO postComment(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "wf_run_id") String wf_run_id,
            @PathVariable(name = "user_task_guid") String user_task_guid,
            @RequestBody CommentContentRequest commentContentRequest)
            throws JsonProcessingException {

        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        CustomIdentityProviderProperties actualIdPProperties =
                getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

        var userNameFromToken = (String) tokenClaims.get(actualIdPProperties.getUsernameClaim());
        var subFromToken = (String) tokenClaims.get("sub");
        String userIdForComment = subFromToken + DELIMITER + userNameFromToken;
        PutCommentRequest request = PutCommentRequest.builder()
                .comment(commentContentRequest.getComment())
                .wfRunId(wf_run_id)
                .userTaskRunGuid(user_task_guid)
                .build();
        return userTaskService.comment(request, userIdForComment, tenantId, false);
    }

    @Operation(
            summary = "Edit a comment on a UserTask",
            description =
                    "Edits an existing comment on a UserTask. Returns the updated AuditEventDTO representing the edit event.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "The comment was successfully edited.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AuditEventDTO.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid input for editing a comment.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized. The tenant ID or access token is invalid.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Either the comment, UserTaskRun, or WfRun could not be found.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "The comment could not be edited due to failed pre-condition.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "An internal server error occurred while editing the comment.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PutMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/comment/{comment_id}")
    @ResponseStatus(HttpStatus.OK)
    public AuditEventDTO editComment(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "wf_run_id") String wfRunId,
            @PathVariable(name = "user_task_guid") String userTaskGuid,
            @PathVariable(name = "comment_id") int commentId,
            @RequestBody CommentContentRequest commentContentRequest)
            throws JsonProcessingException {

        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        CustomIdentityProviderProperties actualIdPProperties =
                getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

        var userNameFromToken = (String) tokenClaims.get(actualIdPProperties.getUsernameClaim());
        var subFromToken = (String) tokenClaims.get("sub");
        String userIdForComment = subFromToken + DELIMITER + userNameFromToken;

        EditCommentRequest request = EditCommentRequest.builder()
                .comment(commentContentRequest.getComment())
                .commentId(commentId)
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskGuid)
                .build();

        return userTaskService.editComment(request, userIdForComment, tenantId, false);
    }

    @Operation(
            summary = "Delete a comment on a UserTask",
            description =
                    "Deletes a comment from a UserTask. Returns the corresponding AuditEventDTO that represents the deleted comment.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "The comment was successfully deleted.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AuditEventDTO.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid input provided. The delete request is malformed.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized. The tenant ID or access token is invalid.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Either the comment, UserTaskRun, or WfRun could not be found. ",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "The comment could not be deleted due to the current state of the UserTask.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "An internal server error occurred while deleting the comment.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @DeleteMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/comment/{comment_id}")
    public AuditEventDTO deleteComment(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "wf_run_id") String wfRunId,
            @PathVariable(name = "user_task_guid") String userTaskGuid,
            @PathVariable(name = "comment_id") int commentId)
            throws JsonProcessingException {

        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        CustomIdentityProviderProperties actualIdPProperties =
                getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

        var userNameFromToken = (String) tokenClaims.get(actualIdPProperties.getUsernameClaim());
        var subFromToken = (String) tokenClaims.get("sub");
        String userIdForComment = subFromToken + DELIMITER + userNameFromToken;

        DeleteCommentRequest request = DeleteCommentRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskGuid)
                .commentId(commentId)
                .build();

        var response = userTaskService.deleteComment(request, userIdForComment, tenantId, false);

        return response;
    }

    @Operation(
            summary = "Get comments on a UserTask",
            description = "Retrieves a list of AuditEventDTO's representing the latest event for a comment.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Comments successfully retrieved.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        array = @ArraySchema(schema = @Schema(implementation = AuditEventDTO.class)))),
                @ApiResponse(responseCode = "204", description = "UserTaskRun does not have any comments."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid input provided (e.g., malformed request or missing path variables).",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized. The tenant ID or access token is invalid.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "The UserTaskRun was not found.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Internal server error occurred while retrieving comments.",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping("/{tenant_id}/tasks/{wfRunId}/{userTaskRunGuid}/comments")
    public ResponseEntity<List<AuditEventDTO>> getComments(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "wfRunId") String wfRunId,
            @PathVariable(name = "userTaskRunGuid") String userTaskRunGuid)
            throws JsonProcessingException {

        var result = userTaskService.getComment(wfRunId, userTaskRunGuid, tenantId);

        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Claim UserTask", description = "Claims a UserTaskRun by assigning it to the requester user.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", content = @Content),
                @ApiResponse(
                        responseCode = "400",
                        description = "Field(s) passed in is/are invalid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Tenant Id is not valid. It could also be triggered when given admin user does not "
                                        + "have permissions to assign users/userGroups to the requested UserTask.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "404",
                        description = "No UserTask data was found in LH Kernel using the given params.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "409",
                        description = "UserTask cannot be claimed.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "412",
                        description = "Failed at a LittleHorse Kernel condition.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @PostMapping("/{tenant_id}/tasks/{wf_run_id}/{user_task_guid}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void claimUserTask(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @PathVariable(name = "wf_run_id") String wfRunId,
            @PathVariable(name = "user_task_guid") String userTaskRunGuid)
            throws JsonProcessingException {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        CustomIdentityProviderProperties actualProperties =
                CustomIdentityProviderProperties.getCustomIdentityProviderProperties(
                        accessToken, identityProviderConfigProperties);

        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

        var userIdFromToken =
                (String) tokenClaims.get(actualProperties.getUserIdClaim().toString());

        IStandardIdentityProviderAdapter identityProviderHandler =
                getIdentityProviderHandler(actualProperties.getVendor(), false);

        Set<String> userGroups = null;

        if (Objects.nonNull(identityProviderHandler)) {
            Map<String, Object> params = Map.of("accessToken", accessToken);
            UserGroupListDTO myUserGroups = identityProviderHandler.getMyUserGroups(params);

            userGroups = myUserGroups.getGroups().stream()
                    .map(UserGroupDTO::getName)
                    .collect(Collectors.toUnmodifiableSet());
        }

        userTaskService.claimUserTask(userIdFromToken, userGroups, wfRunId, userTaskRunGuid, tenantId, false);
    }

    @Operation(
            summary = "Get Groups",
            description =
                    "Gets all Groups from a specific identity provider of a specific tenant for the requesting user.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserGroupListDTO.class))
                        }),
                @ApiResponse(
                        responseCode = "400",
                        description = "Field(s) passed in is/are invalid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
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
    @GetMapping("/{tenant_id}/groups")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserGroupListDTO> getUserGroupsFromIdentityProvider(
            @PathVariable(name = "tenant_id") String tenantId,
            @RequestHeader(name = "Authorization") String accessToken) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            CustomIdentityProviderProperties actualProperties =
                    CustomIdentityProviderProperties.getCustomIdentityProviderProperties(
                            accessToken, identityProviderConfigProperties);

            Map<String, Object> params = Map.of("accessToken", accessToken);
            IStandardIdentityProviderAdapter identityProviderHandler =
                    getIdentityProviderHandler(actualProperties.getVendor(), true);

            var response = identityProviderHandler.getMyUserGroups(params);

            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                    .build();
        }
    }

    @Operation(summary = "Get User Info", description = "Gets the requesting user's info.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
                        }),
                @ApiResponse(
                        responseCode = "400",
                        description = "Field(s) passed in is/are invalid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
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
    @GetMapping("/{tenant_id}/userInfo")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserDTO> getMyUserInfo(
            @RequestHeader(name = "Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId) {
        if (!tenantService.isValidTenant(tenantId, accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var userId = (String) tokenClaims.get(USER_ID_CLAIM);

            CustomIdentityProviderProperties actualProperties =
                    CustomIdentityProviderProperties.getCustomIdentityProviderProperties(
                            accessToken, identityProviderConfigProperties);

            Map<String, Object> params = Map.of("userId", userId, "accessToken", accessToken);
            IStandardIdentityProviderAdapter identityProviderHandler =
                    getIdentityProviderHandler(actualProperties.getVendor(), true);

            var response = identityProviderHandler.getUserInfo(params);

            return ResponseEntity.ok(response);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token while trying to fetch User info.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                    .build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                    .build();
        }
    }

    @Operation(
            summary = "Get Claimable UserTasks",
            description = "Gets all UserTasks assigned to an specific userGroup that the user belongs to.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "List of unique UserTasks with some basic attributes. Optionally, it will retrieve a bookmark "
                                        + "field that is used for pagination purposes.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserTaskRunListDTO.class))
                        }),
                @ApiResponse(
                        responseCode = "401",
                        description = "Tenant Id is not valid.",
                        content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    @GetMapping("/{tenant_id}/tasks/claimable")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskRunListDTO> getClaimableTasks(
            @RequestHeader("Authorization") String accessToken,
            @PathVariable(name = "tenant_id") String tenantId,
            @RequestParam(name = "earliest_start_date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime earliestStartDate,
            @RequestParam(name = "latest_start_date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime latestStartDate,
            @RequestParam(name = "user_group_id") String userGroupId,
            @RequestParam(name = "limit") Integer limit,
            @RequestParam(name = "bookmark", required = false) String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId, accessToken)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED))
                        .build();
            }

            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);

            // Here we are hardcoding the UNASSIGNED status on purpose since that is the way in which we can fetch
            // claimable tasks from LH Kernel
            UserTaskStatus claimableStatus = UserTaskStatus.UNASSIGNED;

            var additionalFilters = UserTaskRequestFilter.buildUserTaskRequestFilter(
                    earliestStartDate, latestStartDate, claimableStatus, null);
            var parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            CustomIdentityProviderProperties actualProperties =
                    getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

            var userIdFromToken =
                    (String) tokenClaims.get(actualProperties.getUserIdClaim().toString());

            IStandardIdentityProviderAdapter identityProviderHandler =
                    getIdentityProviderHandler(actualProperties.getVendor(), false);

            boolean hasIdPAdapter = Objects.nonNull(identityProviderHandler);

            if (hasIdPAdapter) {
                identityProviderHandler.validateUserGroup(userGroupId, accessToken);
                UserGroupDTO foundUserGroup = identityProviderHandler.getUserGroup(
                        Map.of("userGroupId", userGroupId, "accessToken", accessToken));

                if (Objects.nonNull(foundUserGroup)) {
                    userGroupId = foundUserGroup.getName();
                }
            }

            UserTaskRunListDTO response = userTaskService.getTasks(
                    tenantId, userIdFromToken, userGroupId, additionalFilters, limit, parsedBookmark, false);

            if (!CollectionUtils.isEmpty(response.getUserTasks()) && hasIdPAdapter) {
                response.addAssignmentDetails(accessToken, identityProviderHandler, actualProperties);
            }

            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()))
                    .build();
        } catch (JsonProcessingException e) {
            log.error(
                    "Something went wrong when getting claims from token while trying to fetch claimable UserTaskRuns.");
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                    .build();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                    .build();
        }
    }

    private IStandardIdentityProviderAdapter getIdentityProviderHandler(
            @NonNull IdentityProviderVendor vendor, boolean strict) {
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
