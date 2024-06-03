package io.littlehorse.usertasks.services;

import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.Tenant;
import io.littlehorse.sdk.common.proto.TenantId;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient = mock();

    private final TenantService tenantService = new TenantService(lhClient);

    @Test
    void isValidTenant_shouldReturnFalseWhenNoMatchingTenantIsFound() throws NotFoundException {
        var tenantIdToValidate = "invalidTenantId";

        when(lhClient.getTenant(any(TenantId.class))).thenReturn(null);

        assertFalse(tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnTrueWhenTenantIdIsPresentInLHServer() throws NotFoundException {
        String tenantIdToValidate = "my-tenant-id";

        when(lhClient.getTenant(any(TenantId.class))).thenReturn(Tenant.getDefaultInstance());

        assertTrue(tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient).getTenant(any(TenantId.class));
    }
}
