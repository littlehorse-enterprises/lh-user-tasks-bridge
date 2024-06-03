package io.littlehorse.usertasks.controllers;

import io.littlehorse.usertasks.services.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated()")
@Slf4j
public class InitController {

    private final TenantService tenantService;

    public InitController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/{tenant_id}/init")
    public ResponseEntity<Object> initIntegrationForTenant(@RequestHeader("Authorization") String accessToken,
                                                           @PathVariable(name = "tenant_id") String tenantId) {
        if (!tenantService.isValidTenant(tenantId)) {
            return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)).build();
        }

        log.info("Integration successfully initiated!");
        return ResponseEntity.ok().build();
    }
}
