package io.littlehorse.usertasks.services;

import com.google.protobuf.ByteString;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.SearchTenantRequest;
import io.littlehorse.sdk.common.proto.TenantId;
import io.littlehorse.sdk.common.proto.TenantIdList;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

@Service
public class TenantService {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient;

    TenantService(LittleHorseGrpc.LittleHorseBlockingStub lhClient) {
        this.lhClient = lhClient;
    }

    //TODO: The way we ware verifying if a tenant is valid will probably change once we set production-ready OIDC configuration per tenant
    public boolean isValidTenant(@NonNull String tenantId) throws NotFoundException {
        var firstSearchResults = lhClient.searchTenant(SearchTenantRequest.newBuilder().build());

        if (firstSearchResults.getResultsList().isEmpty()) {
            throw new NotFoundException("There are no tenants configured in server");
        }

        var foundTenantId = firstSearchResults.getResultsList().stream().anyMatch(matchesTenantId(tenantId));
        var hasBookmark = firstSearchResults.hasBookmark();
        var bookmark = firstSearchResults.getBookmark();

        while (!foundTenantId && hasBookmark) {
            TenantIdList subsequentSearchResults = getSubsequentSearchTenantResults(bookmark);

            foundTenantId = subsequentSearchResults.getResultsList().stream().anyMatch(matchesTenantId(tenantId));
            hasBookmark = subsequentSearchResults.hasBookmark();
            bookmark = subsequentSearchResults.getBookmark();
        }

        return foundTenantId;
    }

    private Predicate<TenantId> matchesTenantId(String tenantId) {
        return existingTenantId -> existingTenantId.getId().equalsIgnoreCase(tenantId);
    }

    private TenantIdList getSubsequentSearchTenantResults(ByteString bookmark) {
        return lhClient.searchTenant(SearchTenantRequest.newBuilder()
                .setBookmark(bookmark)
                .build());
    }
}
