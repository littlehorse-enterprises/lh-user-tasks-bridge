package io.littlehorse.usertasks.models.responses;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

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
