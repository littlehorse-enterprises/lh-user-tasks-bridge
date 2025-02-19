package io.littlehorse.usertasks.models.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Builder
@AllArgsConstructor
@Data
public class IdentityProviderListDTO {
    private Set<IdentityProviderDTO> providers;
}
