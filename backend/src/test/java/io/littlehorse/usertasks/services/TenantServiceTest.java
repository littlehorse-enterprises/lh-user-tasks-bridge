package io.littlehorse.usertasks.services;

import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
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
import io.littlehorse.usertasks.models.responses.IdentityProviderDTO;
import io.littlehorse.usertasks.models.responses.IdentityProviderListDTO;
import io.littlehorse.usertasks.util.TokenUtil;
import io.littlehorse.usertasks.util.enums.CustomUserIdClaim;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TenantServiceTest {
    private final Map<String, LittleHorseGrpc.LittleHorseBlockingStub> lhClients = mock();
    private final LittleHorseGrpc.LittleHorseBlockingStub lhTenantClient = mock();
    private final IdentityProviderConfigProperties identityProviderConfigProperties = mock();

    private final TenantService tenantService = new TenantService(lhClients, identityProviderConfigProperties);

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
    private final String OKTA_ACCESS_TOKEN = "eyJraWQiOiJHWFJic1c2clV4S3J5SGJ1NjNhU0tNX1pQb1FQOF9IdTBoVng0NUViRlNJIiwiYWxnIjoi" +
            "UlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULlpWUjFiNmN4cmFRMjVzRmJvVGFEU0twLU9yTTlPcVJZY3lueW1VbjVHRVEiLCJpc3MiOiJod" +
            "HRwczovL3RyaWFsLTU5MDM4NzUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNzMzODY1N" +
            "DgyLCJleHAiOjE3MzM4NjkwODIsImNpZCI6IjBvYW03NTd4aXVGdlIwdjhRNjk3IiwidWlkIjoiMDB1bTczcnh0ZnhqbklWUUI2OTciLCJzY" +
            "3AiOlsicHJvZmlsZSIsImVtYWlsIiwib3BlbmlkIl0sImF1dGhfdGltZSI6MTczMzg2NTQ4MCwic3ViIjoiamhvc2VwQGxpdHRsZWhvcnNlL" +
            "mlvIiwiYWxsb3dlZF90ZW5hbnQiOiJkZWZhdWx0In0.btq3Iuvqo57UZDvsO_xtp2Sra502GrAPJvq_7SRqZ6xFtmM36T5yIDKfyC0LG0Dhy" +
            "XPLe66ZAumukMVXRhzdaHHLNcUK5U4X3Y5rn_nd1ptiYNKwRyzGpMcRpY2Eid4yo5nx9z9B4MTa5lhHj0KwGnkqSJfZkNW_-I1gJt0zKca783" +
            "kOTPdoAbfSdNCayJ8WlGqmKr_-zA7hmjRIGs2ZHA5KfDH_81SzFPBrJyFGaAzwHaRmHbk1K3fg6eJC4Uw07oixw7TPiaSla47p6NDG-kzshi" +
            "NgR9sJHYf2ALHATYYTkV6IKLNbjoXuo-vuqus9kqkoXNb_QyR43hw6qZaH_A";

    @BeforeEach
    void init() {
        when(lhClients.get(anyString())).thenReturn(lhTenantClient);
    }

    @Test
    void isValidTenant_shouldThrowNullPointerExceptionWhenTenantIdIsNull() {
        assertThrows(NullPointerException.class, () -> tenantService.isValidTenant(null, STUBBED_ACCESS_TOKEN));

        verify(lhTenantClient, never()).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldThrowNullPointerExceptionWhenAccessTokenIsNull() {
        var tenantIdToValidate = "someTenantId";
        assertThrows(NullPointerException.class, () -> tenantService.isValidTenant(tenantIdToValidate, null));

        verify(lhTenantClient, never()).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenNoMatchingTenantIsFound() {
        var tenantIdToValidate = "invalidTenantId";

        when(lhTenantClient.getTenant(any(TenantId.class))).thenReturn(null);

        assertFalse(tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        verify(lhTenantClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsNotFoundException() {
        var tenantIdToValidate = "someTenant";

        when(lhTenantClient.getTenant(any(TenantId.class))).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertFalse(tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        verify(lhTenantClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsUnhandledException() {
        var tenantIdToValidate = "someOtherTenant";

        when(lhTenantClient.getTenant(any(TenantId.class))).thenThrow(new StatusRuntimeException(Status.ABORTED));

        assertThrows(StatusRuntimeException.class,
                () -> tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        verify(lhTenantClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenServerAPIThrowsAnExceptionDifferentFromNotFoundException() {
        var tenantIdToValidate = "someTenant";
        var expectedExceptionMessage = "ERROR: Some pretty weird issue on LH Server";

        when(lhTenantClient.getTenant(any(TenantId.class))).thenThrow(new RuntimeException(expectedExceptionMessage));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhTenantClient).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldThrowExceptionWhenRequestTenantIsNotFoundWithinTheConfiguration() {
        var tenantIdToValidate = "someTenant";
        var expectedExceptionMessage = "Could not find a matching configured tenant";

        when(lhClients.get(anyString())).thenReturn(null);

        SecurityException exception = assertThrows(SecurityException.class,
                () -> tenantService.isValidTenant(tenantIdToValidate, STUBBED_ACCESS_TOKEN));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhTenantClient, never()).getTenant(any(TenantId.class));
    }

    @Test
    void isValidTenant_shouldThrowResponseStatusExceptionAsInternalServerErrorWhenAnExceptionIsThrownWhileReadingClaimsFromToken() {
        var tenantIdToValidate = "someTenant";

        try (MockedStatic<TokenUtil> mockStaticTokenUtil = mockStatic(TokenUtil.class)) {
            when(lhTenantClient.getTenant(any(TenantId.class))).thenReturn(Tenant.getDefaultInstance());
            mockStaticTokenUtil.when(() -> TokenUtil.getTokenClaims(anyString()))
                    .thenThrow(new UnrecognizedPropertyException(mock(JsonParser.class), "error message",
                            mock(JsonLocation.class), TokenUtil.class, "some-property", Collections.emptyList()));

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> tenantService.isValidTenant(tenantIdToValidate, "wrongToken"));

            int expectedHttpErrorCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
            assertEquals(expectedHttpErrorCode, exception.getBody().getStatus());

            verify(lhTenantClient).getTenant(any(TenantId.class));
        }
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenClientIdDoesNotMatchConfigurationProperties() {
        var configuredTenant = "default";
        var tenantIdToValidate = "default";
        URI fakeUri = URI.create("https://trial-5903875.okta.com/oauth2/default");
        CustomIdentityProviderProperties properties = getCustomIdentityProviderPropertiesWithOkta(fakeUri, configuredTenant);

        when(lhTenantClient.getTenant(any(TenantId.class))).thenReturn(Tenant.getDefaultInstance());
        when(identityProviderConfigProperties.getOps()).thenReturn(List.of(properties));

        ResponseStatusException responseStatusException = assertThrows(ResponseStatusException.class,
                () -> tenantService.isValidTenant(tenantIdToValidate, OKTA_ACCESS_TOKEN));

        int expectedErrorCode = HttpStatus.UNAUTHORIZED.value();
        assertEquals(expectedErrorCode, responseStatusException.getBody().getStatus());

        verify(lhTenantClient).getTenant(any(TenantId.class));
    }

    @Test
    void getTenantIdentityProviderConfig_shouldThrowNullPointerExceptionIfNullTenantIsReceived() {
        assertThrows(NullPointerException.class, ()-> tenantService.getTenantIdentityProviderConfig(null));
    }

    @Test
    void getTenantIdentityProviderConfig_shouldReturnEmptyWhenNoProviderConfigIsFoundForAGivenTenant() {
        var configuredTenant = "default";
        var requestedTenant = "some-tenant";
        URI fakeUri = URI.create("https://trial-5903875.okta.com/oauth2/default");
        CustomIdentityProviderProperties properties = getCustomIdentityProviderPropertiesWithOkta(fakeUri, configuredTenant);

        when(identityProviderConfigProperties.getOps()).thenReturn(List.of(properties));

        IdentityProviderListDTO providerConfigs = tenantService.getTenantIdentityProviderConfig(requestedTenant);

        assertTrue(providerConfigs.getProviders().isEmpty());

        verify(identityProviderConfigProperties).getOps();
    }

    @Test
    void getTenantIdentityProviderConfig_shouldReturnProviderConfigWhenThereIsAMatchingTenantConfigFound() {
        var configuredTenant = "default";
        var requestedTenant = "default";
        URI fakeUri = URI.create("http://keycloak:8888/realms/default");
        CustomIdentityProviderProperties properties = getCustomIdentityProviderPropertiesWithKeycloak(fakeUri, configuredTenant);

        when(identityProviderConfigProperties.getOps()).thenReturn(List.of(properties));

        IdentityProviderListDTO providerConfigs = tenantService.getTenantIdentityProviderConfig(requestedTenant);

        Set<IdentityProviderDTO> foundProvidersConfig = providerConfigs.getProviders();

        int expectedTotalProviderConfigsCount = 1;

        assertFalse(foundProvidersConfig.isEmpty());
        assertEquals(expectedTotalProviderConfigsCount, foundProvidersConfig.size());
        assertEquals(IdentityProviderVendor.KEYCLOAK, foundProvidersConfig.iterator().next().getVendor());

        verify(identityProviderConfigProperties).getOps();
    }

    @Test
    void getTenantIdentityProviderConfig_shouldReturnProviderConfigWhenThereIsAMatchingTenantConfigFoundWithDifferentClients() {
        var configuredTenant = "default";
        var requestedTenant = "default";
        String issuerURL = "https://trial-5903875.okta.com/oauth2/default";
        URI fakeUri = URI.create(issuerURL);
        CustomIdentityProviderProperties properties = getCustomIdentityProviderPropertiesWithOkta(fakeUri, configuredTenant);

        when(identityProviderConfigProperties.getOps()).thenReturn(List.of(properties));

        IdentityProviderListDTO providerConfigs = tenantService.getTenantIdentityProviderConfig(requestedTenant);

        assertTrue(providerConfigs.getProviders().stream()
                .allMatch(providerConfig -> providerConfig.getVendor() == IdentityProviderVendor.OKTA
                        && StringUtils.equalsIgnoreCase(issuerURL, providerConfig.getIssuer())));

        Set<IdentityProviderDTO> foundProvidersConfig = providerConfigs.getProviders();

        int expectedTotalProviderConfigsCount = 2;

        assertFalse(foundProvidersConfig.isEmpty());
        assertEquals(expectedTotalProviderConfigsCount, foundProvidersConfig.size());

        verify(identityProviderConfigProperties).getOps();
    }

    @Test
    void getTenantIdentityProviderConfig_shouldReturnProviderConfigWhenThereAreSeveralMatchingTenantConfigsFound() {
        var configuredTenant = "default";
        var requestedTenant = "default";
        String oktaIssuerURL = "https://trial-5903875.okta.com/oauth2/default";
        String keycloakIssuerURL = "http://keycloak:8888/realms/default";
        URI fakeOktaUri = URI.create(oktaIssuerURL);
        URI fakeKeycloakUri = URI.create(keycloakIssuerURL);
        CustomIdentityProviderProperties properties1 = getCustomIdentityProviderPropertiesWithOkta(fakeOktaUri, configuredTenant);
        CustomIdentityProviderProperties properties2 = getCustomIdentityProviderPropertiesWithKeycloak(fakeKeycloakUri, configuredTenant);

        when(identityProviderConfigProperties.getOps()).thenReturn(List.of(properties1, properties2));

        IdentityProviderListDTO providerConfigs = tenantService.getTenantIdentityProviderConfig(requestedTenant);

        Set<IdentityProviderDTO> foundProvidersConfig = providerConfigs.getProviders();

        int expectedTotalProviderConfigsCount = 3;
        int expectedOktaProviderConfigsCount = 2;
        int expectedKeycloakProviderConfigCount = 1;

        assertFalse(foundProvidersConfig.isEmpty());
        assertEquals(expectedTotalProviderConfigsCount, foundProvidersConfig.size());
        assertEquals(expectedOktaProviderConfigsCount, foundProvidersConfig.stream()
                .filter(providerConfig -> providerConfig.getVendor() == IdentityProviderVendor.OKTA
                        && StringUtils.equalsIgnoreCase(oktaIssuerURL, providerConfig.getIssuer()))
                .count()
        );
        assertEquals(expectedKeycloakProviderConfigCount, foundProvidersConfig.stream()
                .filter(providerConfig -> providerConfig.getVendor() == IdentityProviderVendor.KEYCLOAK
                        && StringUtils.equalsIgnoreCase(keycloakIssuerURL, providerConfig.getIssuer()))
                .count()
        );

        verify(identityProviderConfigProperties).getOps();
    }

    private CustomIdentityProviderProperties getCustomIdentityProviderPropertiesWithOkta(URI fakeUri, String configuredTenant) {
        var fakeUsernameClaim = "preferred_username";
        IdentityProviderVendor fakeVendor = IdentityProviderVendor.OKTA;
        Set<String> configuredClients = Set.of("user-tasks-bridge-client", "user-tasks-bridge-client-2");
        SpringAddonsOidcProperties.OpenidProviderProperties.SimpleAuthoritiesMappingProperties authority = new SpringAddonsOidcProperties.OpenidProviderProperties.SimpleAuthoritiesMappingProperties();
        authority.setPath("$.someJsonPath.roles");

        return new CustomIdentityProviderProperties(fakeUri, fakeUsernameClaim, CustomUserIdClaim.EMAIL, fakeVendor, "some-okta",
                configuredTenant, configuredClients, "cid", List.of(authority));
    }

    private CustomIdentityProviderProperties getCustomIdentityProviderPropertiesWithKeycloak(URI fakeUri, String configuredTenant) {
        var fakeUsernameClaim = "preferred_username";
        IdentityProviderVendor fakeVendor = IdentityProviderVendor.KEYCLOAK;
        Set<String> configuredClients = Set.of("user-tasks-bridge-client");
        SpringAddonsOidcProperties.OpenidProviderProperties.SimpleAuthoritiesMappingProperties authority = new SpringAddonsOidcProperties.OpenidProviderProperties.SimpleAuthoritiesMappingProperties();
        authority.setPath("$.realm_access.roles");

        return new CustomIdentityProviderProperties(fakeUri, fakeUsernameClaim, CustomUserIdClaim.EMAIL, fakeVendor, "some-keycloak",
                configuredTenant, configuredClients, "azp", List.of(authority));
    }
}
