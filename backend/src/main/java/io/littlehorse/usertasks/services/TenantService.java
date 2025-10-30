package io.littlehorse.usertasks.services;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ALLOWED_TOKEN_CUSTOM_CLAIM;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.Tenant;
import io.littlehorse.sdk.common.proto.TenantId;
import io.littlehorse.usertasks.configurations.IdentityProviderConfigProperties;
import io.littlehorse.usertasks.models.responses.IdentityProviderDTO;
import io.littlehorse.usertasks.models.responses.IdentityProviderListDTO;
import io.littlehorse.usertasks.util.TokenUtil;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class TenantService {
    private final Map<String, LittleHorseGrpc.LittleHorseBlockingStub> lhClients;
    private final IdentityProviderConfigProperties identityProviderConfigProperties;

    TenantService(
            Map<String, LittleHorseGrpc.LittleHorseBlockingStub> lhClients,
            IdentityProviderConfigProperties identityProviderConfigProperties) {
        this.lhClients = lhClients;
        this.identityProviderConfigProperties = identityProviderConfigProperties;
    }

    public boolean isValidTenant(@NonNull String requestTenantId, @NonNull String accessToken) {
        try {
            LittleHorseGrpc.LittleHorseBlockingStub tenantBoundLHClient = getTenantLHClient(requestTenantId);
            Tenant tenant = tenantBoundLHClient.getTenant(
                    TenantId.newBuilder().setId(requestTenantId).build());

            return Objects.nonNull(tenant) && isMatchingPropertiesConfiguration(requestTenantId, accessToken);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.atInfo()
                        .setMessage("Tenant {} was not found in LH Kernel!")
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

    @NonNull
    public IdentityProviderListDTO getTenantIdentityProviderConfig(@NonNull String tenantId) {
        Set<IdentityProviderDTO> tenantConfig = identityProviderConfigProperties.getOps().stream()
                .filter(config -> StringUtils.equalsIgnoreCase(tenantId, config.getTenantId()))
                .map(IdentityProviderDTO::fromConfigProperties)
                .flatMap(providerSet -> providerSet.stream().distinct())
                .collect(Collectors.toSet());

        return IdentityProviderListDTO.builder().providers(tenantConfig).build();
    }

    private LittleHorseGrpc.LittleHorseBlockingStub getTenantLHClient(String tenantId) {
        Optional<LittleHorseGrpc.LittleHorseBlockingStub> optionalTenantClient =
                Optional.ofNullable(lhClients.get(tenantId));

        return optionalTenantClient.orElseThrow(
                () -> new SecurityException("Could not find a matching configured tenant"));
    }

    private boolean isMatchingPropertiesConfiguration(String requestTenantId, String accessToken)
            throws JsonProcessingException {
        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        var tokenTenantId = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);

        // Here we make sure that valid configuration properties actually exist
        getCustomIdentityProviderProperties(accessToken, identityProviderConfigProperties);

        return StringUtils.equalsIgnoreCase(requestTenantId, tokenTenantId);
    }
}
