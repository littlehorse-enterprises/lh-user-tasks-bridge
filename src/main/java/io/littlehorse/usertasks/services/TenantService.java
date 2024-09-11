package io.littlehorse.usertasks.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.Tenant;
import io.littlehorse.sdk.common.proto.TenantId;
import io.littlehorse.usertasks.configurations.IdentityProviderConfigProperties;
import io.littlehorse.usertasks.util.TokenUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ALLOWED_TOKEN_CUSTOM_CLAIM;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.AUTHORIZED_PARTY_CLAIM;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ISSUER_URL_CLAIM;

@Service
@Slf4j
public class TenantService {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient;
    private final IdentityProviderConfigProperties identityProviderConfigProperties;

    TenantService(LittleHorseGrpc.LittleHorseBlockingStub lhClient, IdentityProviderConfigProperties identityProviderConfigProperties) {
        this.lhClient = lhClient;
        this.identityProviderConfigProperties = identityProviderConfigProperties;
    }

    public boolean isValidTenant(@NonNull String requestTenantId, @NonNull String accessToken) {
        try {
            Tenant tenant = lhClient.getTenant(TenantId.newBuilder()
                    .setId(requestTenantId)
                    .build());

            return Objects.nonNull(tenant) && isMatchingPropertiesConfiguration(requestTenantId, accessToken);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.atInfo()
                        .setMessage("Tenant {} was not found in LH Server!")
                        .addArgument(requestTenantId)
                        .log();
            } else {
                throw e;
            }
        } catch (JsonProcessingException e) {
            log.error("Something went wrong getting claims from token while matching properties configuration.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Something went wrong while validating Tenant.", e);
            throw e;
        }

        return false;
    }

    private boolean isMatchingPropertiesConfiguration(String requestTenantId, String accessToken) throws JsonProcessingException {
        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
        var tokenTenantId = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);
        var client = (String) tokenClaims.get(AUTHORIZED_PARTY_CLAIM);

        //Here we make sure that valid configuration properties actually exist
        getCustomIdentityProviderProperties(issuerUrl, tokenTenantId, client, identityProviderConfigProperties);

        return StringUtils.equalsIgnoreCase(requestTenantId, tokenTenantId);
    }
}
