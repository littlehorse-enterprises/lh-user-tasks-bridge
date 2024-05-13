package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.properties.TenantOIDCProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitServiceTest {
    @Mock
    private TenantOIDCProperties tenantOIDCProperties;

    @InjectMocks
    private InitService initService = new InitService();

    private final Set<String> fakeConfiguredTenants = Set.of("tenant-1", "tenant-2", "tenant-3");

    @Test
    void isValidTenant_shouldReturnTrueWhenSetOfConfiguredTenantsContainsTenantId() {
        when(tenantOIDCProperties.getConfiguredTenants()).thenReturn(fakeConfiguredTenants);

        var result = initService.isValidTenant("tenant-1");

        assertTrue(result);

        verify(tenantOIDCProperties).getConfiguredTenants();
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenSetOfConfiguredTenantsDoesNotContainTenantId() {
        when(tenantOIDCProperties.getConfiguredTenants()).thenReturn(fakeConfiguredTenants);

        var result = initService.isValidTenant("tenant-999");

        assertFalse(result);

        verify(tenantOIDCProperties).getConfiguredTenants();
    }

    @Test
    void isValidTenant_shouldThrowExceptionWhenNoTenantsAreConfigured() {
        var expectedExceptionMessage = "There are no tenants configured";
        when(tenantOIDCProperties.getConfiguredTenants()).thenReturn(Set.of());

        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> initService.isValidTenant("tenant-1"));

        assertEquals(expectedExceptionMessage, runtimeException.getMessage());

        verify(tenantOIDCProperties).getConfiguredTenants();
    }
}
