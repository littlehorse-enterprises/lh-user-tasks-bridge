package io.littlehorse.usertasks.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.services.TenantService;
import io.littlehorse.usertasks.services.UserTaskService;
import io.littlehorse.usertasks.util.TokenUtil;
import io.littlehorse.usertasks.util.UserTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;

//TODO: This javadoc comment might be replaced later when OpenAPI/Swagger Specs gets introduced

/**
 * {@code UserController} is a controller that exposes endpoints in charge of handling requests related to non-admin users
 */
@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated()")
@Slf4j
public class UserController {

    //TODO: Might change this constant to a global common Constants class later
    private final String USER_ID_CLAIM = "sub";
    private final TenantService tenantService;
    private final UserTaskService userTaskService;

    public UserController(TenantService tenantService, UserTaskService userTaskService) {
        this.tenantService = tenantService;
        this.userTaskService = userTaskService;
    }

    @GetMapping("/{tenant_id}/myTasks")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskRunListDTO> getMyTasks(@RequestHeader("Authorization") String accessToken,
                                                         @PathVariable(name = "tenant_id") String tenantId,
                                                         @RequestParam(name = "earliest_start_date", required = false)
                                                             LocalDateTime earliestStartDate,
                                                         @RequestParam(name = "latest_start_date", required = false)
                                                             LocalDateTime latestStartDate,
                                                         @RequestParam(name = "status", required = false)
                                                             UserTaskStatus status,
                                                         @RequestParam(name = "type", required = false)
                                                             String type,
                                                         @RequestParam(name = "limit")
                                                             Integer limit,
                                                         @RequestParam(name = "bookmark", required = false)
                                                             String bookmark) {
        try {
            if (!tenantService.isValidTenant(tenantId)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }

            var tokenClaims = TokenUtil.getTokenClaims(accessToken);

            var userIdFromToken = (String) tokenClaims.get(USER_ID_CLAIM);
            var additionalFilters = UserTaskRequestFilter.buildUserTaskRequestFilter(earliestStartDate, latestStartDate, status, type);
            var parsedBookmark = Objects.nonNull(bookmark) ? Base64.decodeBase64(bookmark) : null;

            //TODO: User Group filter is pending
            var optionalUserTasks = userTaskService.getMyTasks(userIdFromToken, null, additionalFilters, limit, parsedBookmark);

            return optionalUserTasks
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new NotFoundException("No UserTasks found with given search criteria"));
        } catch (JsonProcessingException e) {
            log.error("Something went wrong when getting claims from token");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }
}
