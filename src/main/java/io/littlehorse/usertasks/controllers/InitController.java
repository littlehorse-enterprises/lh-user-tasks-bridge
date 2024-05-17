package io.littlehorse.usertasks.controllers;

import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.services.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin
@PreAuthorize("isAuthenticated()")
@Slf4j
public class InitController {

    @Autowired
    private TenantService tenantService;

    @GetMapping("/{tenant_id}/init")
    public ResponseEntity<Object> initIntegrationForTenant(@RequestHeader("Authorization") String accessToken,
                                                           @PathVariable(name = "tenant_id") String tenantId) {
        try {
            if (!tenantService.isValidTenant(tenantId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("TenantId: %s is not configured yet.", tenantId));
            }
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }

        log.info("Integration successfully initiated!");
        return ResponseEntity.ok().build();
    }
}
