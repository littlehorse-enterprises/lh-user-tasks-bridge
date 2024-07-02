package io.littlehorse.usertasks.services;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.Tenant;
import io.littlehorse.sdk.common.proto.TenantId;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class TenantService {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient;

    TenantService(LittleHorseGrpc.LittleHorseBlockingStub lhClient) {
        this.lhClient = lhClient;
    }

    //TODO: The way we ware verifying if a tenant is valid will probably change once we set production-ready OIDC configuration per tenant
    public boolean isValidTenant(@NonNull String tenantId) {
        Tenant tenant = null;
        try {
            tenant = lhClient.getTenant(TenantId.newBuilder()
                    .setId(tenantId)
                    .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.atInfo()
                        .setMessage("Tenant {} was not found in LH Server!")
                        .addArgument(tenantId)
                        .log();
            } else {
                throw e;
            }
        } catch (Exception e) {
            log.error("Something went wrong while getting Tenant from LH Server.", e);
            throw e;
        }

        return Objects.nonNull(tenant);
    }
}
