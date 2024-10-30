package io.littlehorse.usertasks.services;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.Tenant;
import io.littlehorse.sdk.common.proto.TenantId;
import io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties;
import io.littlehorse.usertasks.configurations.IdentityProviderConfigProperties;
import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import io.littlehorse.usertasks.util.TokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient = mock();
    private final IdentityProviderConfigProperties identityProviderConfigProperties = mock();

    private final TenantService tenantService = new TenantService(lhClient, identityProviderConfigProperties);

    private final String STUBBED_ACCESS_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJIbkdSc3I1eGpub1UyN0k" +
            "ycVBSMUYwMHEzNlRSaW9iQ3RESU5ET1NFNnNNIn0.eyJleHAiOjE3MjU5MjA0OTAsImlhdCI6MTcyNTkyMDE5MCwianRpIjoiNGY4MzEwZW" +
            "ItNjIyYy00ZDU3LWEzYzYtZDA2MzM0Zjc5ZjI2IiwiaXNzIjoiaHR0cDovL3VzZXItdGFza3Mta2V5Y2xvYWs6ODg4OC9yZWFsbXMvbGgiL" +
            "CJhdWQiOlsicmVhbG0tbWFuYWdlbWVudCIsImFjY291bnQiXSwic3ViIjoiMjdhOGNhNTAtNGFkZi00MjE4LTgwNTQtMmI1ZTVlMDhhMGZh" +
            "IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoidXNlci10YXNrcy1jbGllbnQtMiIsInNpZCI6IjdjMDdkMGNmLTc5NWItNGVlNC1hODBlLTBhYzA" +
            "0OGQ4MWFiMiIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImxoLXVzZXItdG" +
            "Fza3MtYWRtaW4iLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtbGgiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfY" +
            "WNjZXNzIjp7InJlYWxtLW1hbmFnZW1lbnQiOnsicm9sZXMiOlsidmlldy11c2VycyIsInF1ZXJ5LWdyb3VwcyIsInF1ZXJ5LXVzZXJzIl19" +
            "LCJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJ" +
            "zY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJMaCBBZG1pbiBVc2VyIiwiYWxsb3" +
            "dlZF90ZW5hbnQiOiJteS1sb2NhbC10ZW5hbnQiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJsaC1hZG1pbi11c2VyIiwiZ2l2ZW5fbmFtZSI6I" +
            "kxoIiwiZmFtaWx5X25hbWUiOiJBZG1pbiBVc2VyIiwiZW1haWwiOiJsaGFkbWludXNlckBzb21lZG9tYWluLmNvbSJ9.FT1Ov-KB7PbZNJz" +
            "3wBEfu7pshDV6_bGClyH7Vz-maeH5YGL8nBrViIKLXEK_ZrPLwn4vmgOPdblm2F0QBdhCzyTh4C8q36OnOa1AQGhMbP0r7uNPJ4NS-THsD8" +
            "I7n-SUzXtw6cJYTeeZHLgfTdYIH3k1sGxw6bpTkk0_lkvba9tmjCD4PwxsQvaoKcz1-32JSMsAe2Cx7MktXXljp1c-G7MnSr4Ina1-3iV0C" +
            "z022L-8Mr3WMLhplRnXOQN3UcE1QWHIAoxyJV8-uc5Ob8-RIL3qG0v60J52l5SZj6dheNtCpObUYrYMfs3abCa5o7SNpPjOuuJ8Qqbe5Yvl" +
            "OsX_bA";

    @Test
    void isValidTenant_shouldThrowNullPointerExceptionWhenTenantIdIsNull() {
        assertThrows(NullPointerException.class, () -> tenantService.isValidTenant(null, STUBBED_ACCESS_TOKEN));

        verify(lhClient, never()).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldThrowNullPointerExceptionWhenAccessTokenIsNull() {
        var tenantIdToValidate = "someTenantId";
        assertThrows(NullPointerException.class, () -> tenantService.isValidTenant(tenantIdToValidate, null));

        verify(lhClient, never()).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenNoMatchingTenantIsFound() {
        var tenantIdToValidate = "invalidTenantId";

        when(lhClient.getTenant(any(TenantId.class))).thenReturn(null);

        assertFalse(tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsNotFoundException() {
        var tenantIdToValidate = "someTenant";

        when(lhClient.getTenant(any(TenantId.class))).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertFalse(tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsUnhandledException() {
        var tenantIdToValidate = "someOtherTenant";

        when(lhClient.getTenant(any(TenantId.class))).thenThrow(new StatusRuntimeException(Status.ABORTED));

        assertThrows(StatusRuntimeException.class,
                () -> tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsAnExceptionDifferentFromNotFoundException() {
        var tenantIdToValidate = "someTenant";
        var expectedExceptionMessage = "ERROR: Some pretty weird issue on LH Server";

        when(lhClient.getTenant(any(TenantId.class))).thenThrow(new RuntimeException(expectedExceptionMessage));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldThrowResponseStatusExceptionAsInternalServerErrorWhenAnExceptionIsThrownWhileReadingClaimsFromToken() {
        var tenantIdToValidate = "someTenant";

        try (MockedStatic<TokenUtil> mockStaticTokenUtil = mockStatic(TokenUtil.class)) {
            when(lhClient.getTenant(any(TenantId.class))).thenReturn(Tenant.getDefaultInstance());
            mockStaticTokenUtil.when(() -> TokenUtil.getTokenClaims(anyString()))
                    .thenThrow(new UnrecognizedPropertyException(mock(JsonParser.class), "error message",
                            mock(JsonLocation.class), TokenUtil.class, "some-property", Collections.emptyList()));

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> tenantService.isValidTenant(tenantIdToValidate, "wrongToken"));

            int expectedHttpErrorCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
            assertEquals(expectedHttpErrorCode, exception.getBody().getStatus());

            verify(lhClient).getTenant(any(TenantId.class));
        }
    }

    @Test
    void isValidTenant_shouldReturnTrueWhenTenantIdIsPresentInLHServerAndAlsoInConfigurationProperties() {
        var tenantIdToValidate = "my-local-tenant";
        URI fakeUri = URI.create("http://user-tasks-keycloak:8888/realms/lh");
        var fakeUsernameClaim = "preferred_username";
        IdentityProviderVendor fakeVendor = IdentityProviderVendor.KEYCLOAK;
        Set<String> clients = Set.of("user-tasks-client-2");

        var properties = new CustomIdentityProviderProperties(fakeUri, fakeUsernameClaim, fakeVendor, tenantIdToValidate, clients);

        when(lhClient.getTenant(any(TenantId.class))).thenReturn(Tenant.getDefaultInstance());
        when(identityProviderConfigProperties.getOps()).thenReturn(List.of(properties));

        assertTrue(tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        verify(lhClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenTenantIdIsPresentInLHServerButItIsNotPresentInConfigurationProperties() {
        var configuredTenant = "my-local-tenant";
        var tenantIdToValidate = "some-random-tenant";
        URI fakeUri = URI.create("http://user-tasks-keycloak:8888/realms/lh");
        var fakeUsernameClaim = "preferred_username";
        IdentityProviderVendor fakeVendor = IdentityProviderVendor.KEYCLOAK;
        Set<String> clients = Set.of("user-tasks-client-2");

        var properties = new CustomIdentityProviderProperties(fakeUri, fakeUsernameClaim, fakeVendor, configuredTenant, clients);

        when(lhClient.getTenant(any(TenantId.class))).thenReturn(Tenant.getDefaultInstance());
        when(identityProviderConfigProperties.getOps()).thenReturn(List.of(properties));

        assertFalse(tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        verify(lhClient).getTenant(any(TenantId.class));
    }
}
