package io.littlehorse.usertasks.services;

import com.google.protobuf.ByteString;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.SearchTenantRequest;
import io.littlehorse.sdk.common.proto.TenantId;
import io.littlehorse.sdk.common.proto.TenantIdList;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient = mock();

    private final TenantService tenantService = new TenantService(lhClient);

    @Test
    void isValidTenant_shouldThrowExceptionWhenNoTenantsAreFound() throws NotFoundException {
        var tenantIdToValidate = "invalidTenantId";
        var expectedExceptionMessage = "There are no tenants configured in server";

        when(lhClient.searchTenant(any(SearchTenantRequest.class))).thenReturn(TenantIdList.newBuilder().build());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> tenantService.isValidTenant(tenantIdToValidate));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhClient).searchTenant(any(SearchTenantRequest.class));
    }

    @Test
    void isValidTenant_shouldReturnFalseWhenNoMatchingTenantIsFound() throws NotFoundException {
        var tenantIdToValidate = "invalidTenantId";

        TenantIdList firstSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(buildTenantIdCollection(25))
                .setBookmark(ByteString.empty())
                .build();

        TenantIdList secondSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(buildTenantIdCollection(20))
                .build();

        when(lhClient.searchTenant(any(SearchTenantRequest.class))).thenReturn(firstSearchTenantResult, secondSearchTenantResult);

        assertFalse(tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient, times(2)).searchTenant(any(SearchTenantRequest.class));
    }

    @Test
    void isValidTenant_shouldReturnTrueWhenTenantIdIsPresentInLHServerInFirstSearch() throws NotFoundException {
        String tenantIdToValidate = "my-tenant-id";
        Set<TenantId> existingTenantIds = buildTenantIdCollection(24);
        existingTenantIds.add(TenantId.newBuilder().setId(tenantIdToValidate).build());

        TenantIdList firstSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(existingTenantIds)
                .build();

        when(lhClient.searchTenant(any(SearchTenantRequest.class))).thenReturn(firstSearchTenantResult);

        assertTrue(tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient).searchTenant(any(SearchTenantRequest.class));
    }

    @Test
    void isValidTenant_shouldReturnTrueWhenTenantIdIsPresentInLHServerInSubsequentSearch() throws NotFoundException {
        String tenantIdToValidate = "my-tenant-id";
        Set<TenantId> existingTenantIdsInSubsequentSearch = buildTenantIdCollection(24);
        existingTenantIdsInSubsequentSearch.add(TenantId.newBuilder().setId(tenantIdToValidate).build());

        TenantIdList firstSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(buildTenantIdCollection(25))
                .setBookmark(ByteString.empty())
                .build();

        TenantIdList secondSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(buildTenantIdCollection(25))
                .setBookmark(ByteString.empty())
                .build();

        TenantIdList thirdSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(existingTenantIdsInSubsequentSearch)
                .setBookmark(ByteString.empty())
                .build();

        when(lhClient.searchTenant(any(SearchTenantRequest.class)))
                .thenReturn(firstSearchTenantResult, secondSearchTenantResult, thirdSearchTenantResult);

        assertTrue(tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient, times(3)).searchTenant(any(SearchTenantRequest.class));
    }

    @Test
    void isValidTenant_shouldReturnTrueWhenTenantIdIsPresentInLHServerInLastSearch() throws NotFoundException {
        String tenantIdToValidate = "my-tenant-id";
        Set<TenantId> existingTenantIdsInSubsequentSearch = buildTenantIdCollection(24);
        existingTenantIdsInSubsequentSearch.add(TenantId.newBuilder().setId(tenantIdToValidate).build());

        TenantIdList firstSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(buildTenantIdCollection(25))
                .setBookmark(ByteString.empty())
                .build();

        TenantIdList secondSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(buildTenantIdCollection(25))
                .setBookmark(ByteString.empty())
                .build();

        TenantIdList thirdSearchTenantResult = TenantIdList.newBuilder()
                .addAllResults(existingTenantIdsInSubsequentSearch)
                .build();

        when(lhClient.searchTenant(any(SearchTenantRequest.class)))
                .thenReturn(firstSearchTenantResult, secondSearchTenantResult, thirdSearchTenantResult);

        assertTrue(tenantService.isValidTenant(tenantIdToValidate));

        verify(lhClient, times(3)).searchTenant(any(SearchTenantRequest.class));
    }

    private Set<TenantId> buildTenantIdCollection(int quantityOfTenantIds) {
        Set<TenantId> tenantIds = new HashSet<>();

        for (int i = 0; i < quantityOfTenantIds; i++) {
            tenantIds.add(TenantId.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .build());
        }

        return tenantIds;
    }
}
