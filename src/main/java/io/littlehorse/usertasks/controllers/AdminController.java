package io.littlehorse.usertasks.controllers;

import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.UserTaskDefListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.services.TenantService;
import io.littlehorse.usertasks.services.UserTaskService;
import io.littlehorse.usertasks.util.UserTaskStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import static io.littlehorse.usertasks.util.AuthoritiesConstants.USER_TASKS_ADMIN;

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

    public AdminController(TenantService tenantService, UserTaskService userTaskService) {
        this.tenantService = tenantService;
        this.userTaskService = userTaskService;
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No UserTasks were found for given tenant and/or search criteria.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/admin/tasks")
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
                                                         @RequestParam(name = "limit")
                                                         Integer limit,
                                                         @RequestParam(name = "user_id", required = false)
                                                         String userId,
                                                         @RequestParam(name = "user_group", required = false)
                                                         String userGroup,
                                                         @RequestParam(name = "bookmark", required = false)
                                                         String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            var additionalFilters = UserTaskRequestFilter.buildUserTaskRequestFilter(earliestStartDate, latestStartDate,
                    status, type);
            byte[] parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            Optional<UserTaskRunListDTO> optionalUserTasks = userTaskService.getTasks(userId, userGroup, additionalFilters,
                    limit, parsedBookmark, true);

            return optionalUserTasks
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new NotFoundException("No UserTasks found with given search criteria"));
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage())).build();
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
            @ApiResponse(
                    responseCode = "404",
                    description = "No UserTasks were found for given tenant and/or search criteria.",
                    content = {@Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemDetail.class))}
            )
    })
    @GetMapping("/{tenant_id}/admin/tasksDef")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskDefListDTO> getAllUserTasksDef(@PathVariable(name = "tenant_id") String tenantId,
                                                                 @RequestParam(name = "limit") Integer limit,
                                                                 @RequestParam(name = "bookmark", required = false)
                                                                 String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            byte[] parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            UserTaskDefListDTO allUserTasksDef = userTaskService.getAllUserTasksDef(tenantId, limit, parsedBookmark);

            return ResponseEntity.ok(allUserTasksDef);
        } catch (NotFoundException e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage())).build();
        } catch (Exception e) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage())).build();
        }
    }
}
