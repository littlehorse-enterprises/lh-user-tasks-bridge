package io.littlehorse.usertasks.models.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * {@code IdentityProviderListDTO} is a Data Transfer Object that contains a {@code Set} of {@code IdentityProviderDTO}
 *
 * @see java.util.Set
 * @see io.littlehorse.usertasks.models.responses.IdentityProviderDTO
 */
@Builder
@AllArgsConstructor
@Data
public class IdentityProviderListDTO {
    private Set<IdentityProviderDTO> providers;
}
