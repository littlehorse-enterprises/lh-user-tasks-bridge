package io.littlehorse.usertasks.services;

import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.TenantId;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class TenantService {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient;

    TenantService(LittleHorseGrpc.LittleHorseBlockingStub lhClient) {
        this.lhClient = lhClient;
    }

    //TODO: The way we ware verifying if a tenant is valid will probably change once we set production-ready OIDC configuration per tenant
    public boolean isValidTenant(@NonNull String tenantId) throws NotFoundException {
        var tenant = lhClient.getTenant(TenantId.newBuilder()
                .setId(tenantId)
                .build());

        return Objects.nonNull(tenant);
    }
}
