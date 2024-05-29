package io.littlehorse.usertasks.controllers;

import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.models.responses.SimpleUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.services.TenantService;
import io.littlehorse.usertasks.util.UserTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

//TODO: This javadoc comment might be replaced later when OpenAPI/Swagger Specs gets introduced

/**
 * {@code UserController} is a controller that exposes endpoints in charge of handling requests related to non-admin users
 */
@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated()")
@Slf4j
public class UserController {

    private final TenantService tenantService;

    public UserController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/{tenant_id}/myTasks")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserTaskRunListDTO> getMyTasks(@RequestHeader("Authorization") String accessToken,
                                                         @PathVariable(name = "tenant_id") String tenantId) {
        try {
            if (!tenantService.isValidTenant(tenantId)) {
                return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
            }
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }

        //TODO: Log used just for testing purposes. It MUST be removed later on.
        log.info("User's tenant successfully validated!");

        return ResponseEntity.ok(mockResponse());
    }

    private UserTaskRunListDTO mockResponse() {
        Set<SimpleUserTaskRunDTO> mockUserTasksData = new HashSet<>();

        for (int i = 0; i < 10; i++) {
            SimpleUserTaskRunDTO randomUserTask = SimpleUserTaskRunDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .userTaskDefId(UUID.randomUUID().toString())
                    .userId(i % 2 != 0 ? UUID.randomUUID().toString() : null)
                    .userGroup(i % 2 == 0 ? "The Jedi Order" : null)
                    .status(i % 2 == 0 ? UserTaskStatus.ASSIGNED : UserTaskStatus.DONE)
                    .notes(i % 2 == 0 ? "Here you might see notes from the userTaskRun" : null)
                    .scheduledTime(LocalDateTime.now())
                    .build();
            mockUserTasksData.add(randomUserTask);
        }

        return UserTaskRunListDTO.builder()
                .userTasks(mockUserTasksData)
                .build();
    }
}
