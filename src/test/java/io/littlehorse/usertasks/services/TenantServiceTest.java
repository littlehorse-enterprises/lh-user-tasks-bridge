package io.littlehorse.usertasks.services;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.Tenant;
import io.littlehorse.sdk.common.proto.TenantId;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient = mock();

    private final TenantService tenantService = new TenantService(lhClient);

    @Test
    void isValidTenant_shouldThrowNullPointerExceptionWhenTenantIdIsNull() throws NotFoundException {
        assertThrows(NullPointerException.class, () -> tenantService.isValidTenant(null));

        verify(lhClient, never()).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenNoMatchingTenantIsFound() throws NotFoundException {
        var tenantIdToValidate = "invalidTenantId";

        when(lhClient.getTenant(any(TenantId.class))).thenReturn(null);

        assertFalse(tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsNotFoundException() throws NotFoundException {
        var tenantIdToValidate = "someTenant";

        when(lhClient.getTenant(any(TenantId.class))).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertFalse(tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsUnhandledException() throws NotFoundException {
        var tenantIdToValidate = "someOtherTenant";

        when(lhClient.getTenant(any(TenantId.class))).thenThrow(new StatusRuntimeException(Status.ABORTED));

        assertThrows(StatusRuntimeException.class, () -> tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsAnExceptionDifferentFromNotFoundException() throws NotFoundException {
        var tenantIdToValidate = "someTenant";
        var expectedExceptionMessage = "ERROR: Some pretty weird issue on LH Server";

        when(lhClient.getTenant(any(TenantId.class))).thenThrow(new RuntimeException(expectedExceptionMessage));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> tenantService.isValidTenant(tenantIdToValidate));

        assertEquals(expectedExceptionMessage, exception.getMessage());

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
