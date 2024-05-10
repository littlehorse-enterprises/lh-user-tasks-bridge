package io.littlehorse.usertasks.controllers;

import io.littlehorse.usertasks.services.InitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("isAuthenticated()")
@Slf4j
public class InitController {

    @Autowired
    private InitService initService;

    @GetMapping("/{tenantId}/init")
    public ResponseEntity<Object> initOIDCIntegrationForTenant(@RequestHeader("Authorization") String accessToken,
                                                               @PathVariable String tenantId) {
        if (!initService.isValidTenant(tenantId)) {
            return buildUnsuccessfulResponse(HttpStatusCode.valueOf(404),
                    String.format("TenantId: %s is not configured yet.", tenantId));
        }

        log.info("Integration successfully initiated!");
        return ResponseEntity.ok().build();
    }

    private static ResponseEntity<Object> buildUnsuccessfulResponse(HttpStatusCode httpStatusCode, String detail) {
        log.info("Initialization failed due to: {}", detail);
        return ResponseEntity.of(ProblemDetail.forStatusAndDetail(httpStatusCode, detail)).build();
    }
}
